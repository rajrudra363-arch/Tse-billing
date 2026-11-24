"use strict";

const crypto = require("node:crypto");

const DATABASE_ID = "ai-studio-android-tsebilli-812e5e23-5be6-4359-ba54-48c3c2747cb2";
const PRIMARY_ADMIN_EMAIL = "rajrudra363@gmail.com";
const PASSKEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const TRASH_RETENTION_MS = 24 * 60 * 60 * 1000;

function isValidEmail(email) {
  if (typeof email !== "string") return false;
  const trimmed = email.trim();
  if (trimmed.length < 5 || trimmed.length > 254) return false;
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed);
}

function validateStrongPassword(password) {
  if (typeof password !== "string") {
    return "Password must be at least 8 characters.";
  }
  if (password.length < 8 || password.length > 128) {
    return "Password must be between 8 and 128 characters.";
  }
  if (!/[A-Z]/.test(password)) {
    return "Password must include at least one uppercase letter.";
  }
  if (!/[a-z]/.test(password)) {
    return "Password must include at least one lowercase letter.";
  }
  if (!/[0-9]/.test(password)) {
    return "Password must include at least one number.";
  }
  if (!/[^A-Za-z0-9]/.test(password)) {
    return "Password must include at least one special character.";
  }
  return null;
}

function deriveSafeUsername(requestedUsername, email, name) {
  const candidate = (typeof requestedUsername === "string" && requestedUsername.trim())
    ? requestedUsername.trim()
    : (typeof email === "string" && email.includes("@"))
      ? email.split("@")[0]
      : (name || "tech");
  const cleaned = candidate
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 32);
  if (cleaned.length >= 3) return cleaned;
  return `${cleaned}tech`.slice(0, 8);
}

function isValidFirestoreId(id) {
  return typeof id === "string" && id.length >= 1 && id.length <= 128 && !/[\/\\]/.test(id);
}

function generateActivationPasskey() {
  const bytes = crypto.randomBytes(8);
  let chars = "";
  for (let i = 0; i < 8; i++) {
    chars += PASSKEY_ALPHABET[bytes[i] % PASSKEY_ALPHABET.length];
  }
  return `TSE-${chars.slice(0, 4)}-${chars.slice(4, 8)}`;
}

function normalizePasskey(raw) {
  return String(raw || "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
}

function hashActivationPasskey(raw) {
  const normalized = normalizePasskey(raw);
  if (!normalized) return "";
  return crypto.createHash("sha256").update(normalized).digest("hex");
}

function timingSafeHashEqual(hexA, hexB) {
  if (typeof hexA !== "string" || typeof hexB !== "string") return false;
  if (hexA.length !== hexB.length || hexA.length === 0) return false;
  return crypto.timingSafeEqual(Buffer.from(hexA, "utf8"), Buffer.from(hexB, "utf8"));
}

/**
 * Verifies that the caller is an authenticated, authorized Admin of the target workspace
 * and never a technician or super_admin.
 */
async function assertAuthorizedWorkspaceAdmin({ authCtx, db, workspaceId, HttpsError }) {
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Admin authentication is required.");
  }
  if (!isValidFirestoreId(workspaceId)) {
    throw new HttpsError("invalid-argument", "Invalid workspaceId.");
  }

  const wsRef = db.collection("workspaces").doc(workspaceId);
  const wsSnap = await wsRef.get();
  if (!wsSnap.exists) {
    throw new HttpsError("not-found", "Target workspace does not exist.");
  }
  const wsData = wsSnap.data() || {};

  const callerUid = authCtx.uid;
  const callerEmail = String((authCtx.token && authCtx.token.email) || "").trim().toLowerCase();
  const callerEmailVerified = Boolean(authCtx.token && authCtx.token.email_verified);

  const memberSnap = await wsRef.collection("members").doc(callerUid).get();
  const memberRole = memberSnap.exists ? String((memberSnap.data() || {}).role || "").toLowerCase() : "";

  if (memberRole === "technician" || memberRole === "super_admin") {
    throw new HttpsError("permission-denied", "Technicians and Super Admins cannot perform Admin operations.");
  }

  const adminMarkerSnap = await db.collection("admins").doc(callerUid).get();
  const isOwner = wsData.ownerUid === callerUid;
  const isPrimaryAdminEmail = callerEmail === PRIMARY_ADMIN_EMAIL && callerEmailVerified;
  const isAdminMarker = adminMarkerSnap.exists;
  const isAdminMember = memberRole === "admin";

  if (!isOwner && !isPrimaryAdminEmail && !isAdminMarker && !isAdminMember) {
    throw new HttpsError("permission-denied", "Only a verified workspace Admin can perform this operation.");
  }

  return { wsRef, wsData, callerUid, callerEmail };
}

/**
 * Pure dependency-injected callable handler for creating a Technician Firebase Auth
 * account via Admin SDK, issuing a one-time Activation Passkey (only SHA-256 hash saved),
 * and recording non-secret metadata in Cloud Firestore.
 */
async function createTechnicianAccountHandler(request, deps) {
  const { auth, db, FieldValue, HttpsError } = deps;
  const data = (request && request.data) || {};

  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const name = typeof data.name === "string" ? data.name.trim() : "";
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const initialPassword = typeof data.initialPassword === "string" ? data.initialPassword : "";
  const phone = typeof data.phone === "string" ? data.phone.trim().slice(0, 32) : "";

  if (data.role !== undefined && String(data.role).trim().toLowerCase() !== "technician") {
    throw new HttpsError(
      "permission-denied",
      "This endpoint can only provision accounts with the 'technician' role."
    );
  }

  if (name.length < 2 || name.length > 100) {
    throw new HttpsError("invalid-argument", "Technician name must be between 2 and 100 characters.");
  }
  if (!isValidEmail(email)) {
    throw new HttpsError("invalid-argument", "Please enter a valid technician email address.");
  }
  if (email === PRIMARY_ADMIN_EMAIL) {
    throw new HttpsError("invalid-argument", "Primary Admin email cannot be registered as a technician.");
  }

  const passwordError = validateStrongPassword(initialPassword);
  if (passwordError) {
    throw new HttpsError("invalid-argument", passwordError);
  }

  const { wsRef, wsData, callerUid } = await assertAuthorizedWorkspaceAdmin({
    authCtx: request.auth,
    db,
    workspaceId,
    HttpsError
  });

  if (String(wsData.ownerEmail || "").trim().toLowerCase() === email) {
    throw new HttpsError("invalid-argument", "Workspace owner email cannot be used for a technician account.");
  }

  let username = deriveSafeUsername(data.username, email, name);
  const accountsCol = wsRef.collection("accounts");
  const existingAccountSnap = await accountsCol.doc(username).get();
  if (existingAccountSnap.exists) {
    const suffix = Math.floor(100 + Math.random() * 900);
    username = `${username.slice(0, 24)}_${suffix}`;
  }

  const activationPasskey = generateActivationPasskey();
  const activationPasskeyHash = hashActivationPasskey(activationPasskey);

  let userRecord;
  try {
    userRecord = await auth.createUser({
      email,
      password: initialPassword,
      displayName: name,
      emailVerified: true,
      disabled: false
    });
  } catch (err) {
    const code = err && err.code ? String(err.code) : "";
    if (code === "auth/email-already-exists") {
      throw new HttpsError(
        "already-exists",
        "A Firebase Authentication account with this email already exists."
      );
    }
    if (code === "auth/invalid-password") {
      throw new HttpsError("invalid-argument", "Password does not meet Firebase Authentication requirements.");
    }
    throw new HttpsError("internal", `Unable to create Firebase Auth user: ${err.message || "unknown error"}`);
  }

  const techUid = userRecord.uid;
  try {
    if (typeof auth.setCustomUserClaims === "function") {
      await auth.setCustomUserClaims(techUid, {
        role: "technician",
        workspaceId
      });
    }

    const nowTs = FieldValue.serverTimestamp();
    const batch = db.batch();

    batch.update(wsRef, {
      memberUids: FieldValue.arrayUnion(techUid),
      updatedAt: nowTs
    });

    // Never store plaintext password or plaintext activation passkey in Firestore.
    batch.set(wsRef.collection("members").doc(techUid), {
      userId: techUid,
      email,
      displayName: name,
      name,
      username,
      phone,
      role: "technician",
      workspaceId,
      inviteCode: String(wsData.techShareCode || ""),
      mustChangePassword: true,
      authProvider: "password",
      activationPasskeyHash,
      activationPasskeyCreatedAt: nowTs,
      activatedAt: null,
      createdByUid: callerUid,
      createdAt: nowTs,
      updatedAt: nowTs
    });

    batch.set(accountsCol.doc(username), {
      username,
      displayName: name,
      passwordHash: "FIREBASE_EMAIL_AUTH_ONLY",
      role: "technician",
      phone,
      photoBase64: "",
      updatedAt: nowTs
    }, { merge: true });

    await batch.commit();
  } catch (dbErr) {
    try {
      await auth.deleteUser(techUid);
    } catch (_) {
      // Best-effort rollback.
    }
    throw new HttpsError("internal", `Failed to save technician profile in Firestore: ${dbErr.message || "unknown error"}`);
  }

  return {
    uid: techUid,
    email,
    name,
    username,
    role: "technician",
    workspaceId,
    mustChangePassword: true,
    activationPasskey
  };
}

/**
 * Verifies a Technician's authorized email + Admin-issued Activation Passkey on first launch
 * before showing Login screen, returning ONLY the username/workspace bound to that exact email.
 */
async function verifyTechnicianActivationHandler(request, deps) {
  const { auth, db, FieldValue, HttpsError } = deps;
  const data = (request && request.data) || {};

  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const activationPasskey = typeof data.activationPasskey === "string" ? data.activationPasskey : "";
  const workspaceIdHint = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";

  if (!isValidEmail(email)) {
    throw new HttpsError("invalid-argument", "Enter a valid authorized Technician email.");
  }
  if (email === PRIMARY_ADMIN_EMAIL) {
    throw new HttpsError("permission-denied", "Admin accounts cannot use Technician Passkey activation.");
  }
  const suppliedHash = hashActivationPasskey(activationPasskey);
  if (suppliedHash.length < 16) {
    throw new HttpsError("invalid-argument", "Enter the valid Activation Passkey provided by Admin.");
  }

  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(email);
  } catch (err) {
    throw new HttpsError(
      "permission-denied",
      "Email is not authorized or Activation Passkey is incorrect."
    );
  }

  const techUid = userRecord.uid;
  let wsSnap = null;
  if (workspaceIdHint && isValidFirestoreId(workspaceIdHint)) {
    const candidate = await db.collection("workspaces").doc(workspaceIdHint).get();
    if (candidate.exists) wsSnap = candidate;
  }
  if (!wsSnap) {
    const wsQuery = await db.collection("workspaces")
      .where("memberUids", "array-contains", techUid)
      .limit(1)
      .get();
    wsSnap = (wsQuery.docs && wsQuery.docs[0]) || null;
  }

  if (!wsSnap || !wsSnap.exists) {
    throw new HttpsError(
      "permission-denied",
      "No active workspace is assigned to this email."
    );
  }

  const wsId = wsSnap.id || (wsSnap.data() || {}).workspaceId;
  const wsData = wsSnap.data() || {};
  const memberRef = db.collection("workspaces").doc(wsId).collection("members").doc(techUid);
  const memberSnap = await memberRef.get();
  if (!memberSnap.exists) {
    throw new HttpsError("permission-denied", "Technician membership record not found.");
  }

  const memberData = memberSnap.data() || {};
  if (String(memberData.role || "").toLowerCase() !== "technician") {
    throw new HttpsError("permission-denied", "Activation Passkey verification is restricted to Technician accounts.");
  }

  const storedHash = String(memberData.activationPasskeyHash || "");
  if (!timingSafeHashEqual(storedHash, suppliedHash)) {
    throw new HttpsError(
      "permission-denied",
      "Email is not authorized or Activation Passkey is incorrect."
    );
  }

  if (!memberData.activatedAt) {
    const nowTs = FieldValue.serverTimestamp();
    await memberRef.update({
      activatedAt: nowTs,
      updatedAt: nowTs
    });
  }

  const name = String(memberData.name || memberData.displayName || "Technician");
  const username = String(memberData.username || deriveSafeUsername("", email, name));

  return {
    valid: true,
    workspaceId: wsId,
    email,
    username,
    name,
    companyName: String(wsData.companyName || "TOTAL SOLUTION ENTERPRISES"),
    mustChangePassword: Boolean(memberData.mustChangePassword)
  };
}

/**
 * Lets an authorized Admin regenerate a fresh Activation Passkey for an existing Technician.
 */
async function regenerateTechnicianPasskeyHandler(request, deps) {
  const { db, FieldValue, HttpsError } = deps;
  const data = (request && request.data) || {};

  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const technicianUid = typeof data.technicianUid === "string" ? data.technicianUid.trim() : "";

  if (!isValidFirestoreId(technicianUid)) {
    throw new HttpsError("invalid-argument", "Invalid technicianUid.");
  }

  const { wsRef } = await assertAuthorizedWorkspaceAdmin({
    authCtx: request.auth,
    db,
    workspaceId,
    HttpsError
  });

  const memberRef = wsRef.collection("members").doc(technicianUid);
  const memberSnap = await memberRef.get();
  if (!memberSnap.exists) {
    throw new HttpsError("not-found", "Technician member record not found.");
  }
  const memberData = memberSnap.data() || {};
  if (String(memberData.role || "").toLowerCase() !== "technician") {
    throw new HttpsError("permission-denied", "Activation Passkeys can only be regenerated for Technicians.");
  }

  const activationPasskey = generateActivationPasskey();
  const activationPasskeyHash = hashActivationPasskey(activationPasskey);
  const nowTs = FieldValue.serverTimestamp();

  await memberRef.update({
    activationPasskeyHash,
    activationPasskeyCreatedAt: nowTs,
    activatedAt: null,
    updatedAt: nowTs
  });

  return {
    technicianUid,
    email: String(memberData.email || ""),
    username: String(memberData.username || ""),
    activationPasskey
  };
}

/**
 * Records completion of the mandatory first-login password change on the server
 * for the signed-in Technician.
 */
async function completeTechnicianPasswordChangeHandler(request, deps) {
  const { db, FieldValue, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const data = (request && request.data) || {};
  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  if (!isValidFirestoreId(workspaceId)) {
    throw new HttpsError("invalid-argument", "Invalid workspaceId.");
  }

  const memberRef = db.collection("workspaces").doc(workspaceId).collection("members").doc(authCtx.uid);
  const memberSnap = await memberRef.get();
  if (!memberSnap.exists) {
    throw new HttpsError("not-found", "Workspace member record not found.");
  }

  const nowTs = FieldValue.serverTimestamp();
  await memberRef.update({
    mustChangePassword: false,
    passwordChangedAt: nowTs,
    updatedAt: nowTs
  });

  return {
    uid: authCtx.uid,
    workspaceId,
    mustChangePassword: false
  };
}

function toEpochMillis(val) {
  if (val == null) return 0;
  if (typeof val === "number" && Number.isFinite(val)) return val;
  if (typeof val.toMillis === "function") return val.toMillis();
  if (val instanceof Date) return val.getTime();
  return 0;
}

/**
 * Server-authoritative 24-hour trash expiration purge using Firestore server clock.
 */
async function purgeWorkspaceExpiredTrash(db, Timestamp, wsId) {
  const nowTs = Timestamp.now();
  const serverNowMillis = nowTs.toMillis();
  const wsRef = db.collection("workspaces").doc(wsId);
  const trashSnap = await wsRef.collection("trash").where("status", "==", "TRASHED").get();
  const docs = trashSnap.docs || [];

  const expiredDocs = [];
  for (const docSnap of docs) {
    const item = docSnap.data() || {};
    const deletedMillis = toEpochMillis(item.deletedAt) || Number(item.deletedAtMillis || 0);
    const expireMillis = toEpochMillis(item.expireAt) || (deletedMillis > 0 ? deletedMillis + TRASH_RETENTION_MS : 0);
    const expiredByExpireAt = expireMillis > 0 && serverNowMillis >= expireMillis;
    const expiredByDeletedAt = deletedMillis > 0 && (serverNowMillis - deletedMillis) >= TRASH_RETENTION_MS;
    if (expiredByExpireAt || expiredByDeletedAt) {
      expiredDocs.push({ docSnap, item });
    }
  }

  if (expiredDocs.length === 0) {
    return { purgedCount: 0, serverTimeMillis: serverNowMillis };
  }

  const batch = db.batch();
  for (let i = 0; i < expiredDocs.length; i++) {
    const { docSnap, item } = expiredDocs[i];
    batch.delete(docSnap.ref);
    const logId = `log_purge_${serverNowMillis}_${i}`;
    batch.set(wsRef.collection("auditLogs").doc(logId), {
      logId,
      action: "AUTO_PURGE_24H",
      entityType: String(item.entityType || "RECORD"),
      entityId: String(item.entityId || docSnap.id),
      title: String(item.title || "Expired Record"),
      actorUsername: "ServerScheduler",
      actorRole: "system",
      details: "Permanently purged on server after 24h retention window",
      createdAt: serverNowMillis,
      serverCreatedAt: nowTs
    });
  }
  await batch.commit();

  return { purgedCount: expiredDocs.length, serverTimeMillis: serverNowMillis };
}

async function purgeExpiredTrashCallableHandler(request, deps) {
  const { db, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};
  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";

  await assertAuthorizedWorkspaceAdmin({
    authCtx: request.auth,
    db,
    workspaceId,
    HttpsError
  });

  const { purgedCount, serverTimeMillis } = await purgeWorkspaceExpiredTrash(db, Timestamp, workspaceId);
  return { workspaceId, purgedCount, serverTimeMillis };
}

async function scheduledPurgeExpiredTrashHandler(deps) {
  const { db, Timestamp } = deps;
  const wsSnap = await db.collection("workspaces").get();
  const docs = wsSnap.docs || [];
  let totalPurged = 0;
  let lastServerTime = Timestamp.now().toMillis();

  for (const wsDoc of docs) {
    const wsId = wsDoc.id || (wsDoc.data() || {}).workspaceId;
    if (!wsId) continue;
    const res = await purgeWorkspaceExpiredTrash(db, Timestamp, wsId);
    totalPurged += res.purgedCount;
    lastServerTime = res.serverTimeMillis;
  }

  return {
    workspacesChecked: docs.length,
    purgedCount: totalPurged,
    serverTimeMillis: lastServerTime
  };
}

module.exports = {
  DATABASE_ID,
  PRIMARY_ADMIN_EMAIL,
  TRASH_RETENTION_MS,
  isValidEmail,
  validateStrongPassword,
  deriveSafeUsername,
  generateActivationPasskey,
  normalizePasskey,
  hashActivationPasskey,
  createTechnicianAccountHandler,
  verifyTechnicianActivationHandler,
  regenerateTechnicianPasskeyHandler,
  completeTechnicianPasswordChangeHandler,
  purgeWorkspaceExpiredTrash,
  purgeExpiredTrashCallableHandler,
  scheduledPurgeExpiredTrashHandler
};
