"use strict";

const DATABASE_ID = "ai-studio-android-tsebilli-812e5e23-5be6-4359-ba54-48c3c2747cb2";
const PRIMARY_ADMIN_EMAIL = "rajrudra363@gmail.com";

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
    throw new HttpsError("permission-denied", "Technicians and Super Admins cannot create accounts.");
  }

  const adminMarkerSnap = await db.collection("admins").doc(callerUid).get();
  const isOwner = wsData.ownerUid === callerUid;
  const isPrimaryAdminEmail = callerEmail === PRIMARY_ADMIN_EMAIL && callerEmailVerified;
  const isAdminMarker = adminMarkerSnap.exists;
  const isAdminMember = memberRole === "admin";

  if (!isOwner && !isPrimaryAdminEmail && !isAdminMarker && !isAdminMember) {
    throw new HttpsError("permission-denied", "Only a verified workspace Admin can create technicians.");
  }

  return { wsRef, wsData, callerUid, callerEmail };
}

/**
 * Pure dependency-injected callable handler for creating a Technician Firebase Auth
 * account via Admin SDK and recording non-secret metadata in Cloud Firestore.
 */
async function createTechnicianAccountHandler(request, deps) {
  const { auth, db, FieldValue, HttpsError } = deps;
  const data = (request && request.data) || {};

  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const name = typeof data.name === "string" ? data.name.trim() : "";
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const initialPassword = typeof data.initialPassword === "string" ? data.initialPassword : "";
  const phone = typeof data.phone === "string" ? data.phone.trim().slice(0, 32) : "";

  // Reject any attempt to escalate role through payload tampering.
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
    // Enforce technician-only custom claims on Firebase Auth token (defense in depth).
    if (typeof auth.setCustomUserClaims === "function") {
      await auth.setCustomUserClaims(techUid, {
        role: "technician",
        workspaceId
      });
    }

    const nowTs = FieldValue.serverTimestamp();
    const batch = db.batch();

    // Update workspace memberUids so Technician discovers workspace immediately after login.
    batch.update(wsRef, {
      memberUids: FieldValue.arrayUnion(techUid),
      updatedAt: nowTs
    });

    // Save non-secret member record in /workspaces/{workspaceId}/members/{techUid}.
    // NEVER store any password or password hash derived from initialPassword.
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
      createdByUid: callerUid,
      createdAt: nowTs,
      updatedAt: nowTs
    });

    // Sync lightweight account profile into accounts/{username} for technician folders & dropdowns.
    // passwordHash holds a static non-secret sentinel marker satisfying existing >= 16-char rules.
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
    // Roll back Auth user cleanly if Firestore persistence fails so no orphan Auth account remains.
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
    mustChangePassword: true
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

module.exports = {
  DATABASE_ID,
  PRIMARY_ADMIN_EMAIL,
  isValidEmail,
  validateStrongPassword,
  deriveSafeUsername,
  createTechnicianAccountHandler,
  completeTechnicianPasswordChangeHandler
};
