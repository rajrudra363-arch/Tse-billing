"use strict";

const crypto = require("node:crypto");
const {
  base64UrlEncode,
  verifyRegistrationResponse,
  verifyAuthenticationResponse
} = require("./webauthn");

const DATABASE_ID = "ai-studio-android-tsebilli-812e5e23-5be6-4359-ba54-88c3c2747cb2";
const PRIMARY_ADMIN_EMAIL = "rajrudra363@gmail.com";
const OFFICIAL_APP_NAME = "TS – TOTAL SOLUTION";
const OFFICIAL_COMPANY_NAME = "TOTAL SOLUTION";
const TECH_LOGIN_DISABLED_MESSAGE = "Your login has been disabled by Admin. Please contact your Admin.";
const PASSKEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const TRASH_RETENTION_MS = 24 * 60 * 60 * 1000;
const OTP_TTL_MS = 5 * 60 * 1000;
const OTP_RESEND_COOLDOWN_MS = 60 * 1000;
const OTP_BLOCK_DURATION_MS = 15 * 60 * 1000;
const OTP_ABUSE_WINDOW_MS = 15 * 60 * 1000;
const OTP_MAX_REQUESTS_PER_WINDOW = 5;
const OTP_MAX_VERIFY_ATTEMPTS = 5;
const WEBAUTHN_CHALLENGE_TTL_MS = 5 * 60 * 1000;
const DEFAULT_WEBAUTHN_RP_ID = "gen-lang-client-0614473240.web.app";

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

function hashOtpCode(challengeId, email, purpose, otp) {
  return crypto
    .createHash("sha256")
    .update(`${challengeId}|${String(email || "").trim().toLowerCase()}|${String(purpose || "").trim().toUpperCase()}|${String(otp || "").trim()}`)
    .digest("hex");
}

function hashScryptSecret(rawSecret) {
  const salt = crypto.randomBytes(16).toString("hex");
  const derived = crypto.scryptSync(String(rawSecret || ""), salt, 64).toString("hex");
  return `${salt}:${derived}`;
}

function verifyScryptSecret(rawSecret, stored) {
  if (typeof stored !== "string" || !stored.includes(":")) return false;
  const [salt, expectedHex] = stored.split(":");
  if (!salt || !expectedHex) return false;
  const derivedHex = crypto.scryptSync(String(rawSecret || ""), salt, 64).toString("hex");
  return timingSafeHashEqual(derivedHex, expectedHex);
}

function timingSafeHashEqual(hexA, hexB) {
  if (typeof hexA !== "string" || typeof hexB !== "string") return false;
  if (hexA.length !== hexB.length || hexA.length === 0) return false;
  return crypto.timingSafeEqual(Buffer.from(hexA, "utf8"), Buffer.from(hexB, "utf8"));
}

function maskEmail(email) {
  const parts = String(email || "").split("@");
  if (parts.length !== 2) return "***";
  const local = parts[0];
  const shown = local.slice(0, Math.min(2, local.length));
  return `${shown}***@${parts[1]}`;
}

function hashRateLimitKey(email, purpose) {
  return crypto
    .createHash("sha256")
    .update(`${String(email || "").trim().toLowerCase()}|${String(purpose || "").trim().toUpperCase()}`)
    .digest("hex");
}

function formatBlockRemaining(ms) {
  const totalSec = Math.max(1, Math.ceil(ms / 1000));
  const mins = Math.floor(totalSec / 60);
  const secs = totalSec % 60;
  if (mins > 0 && secs > 0) return `${mins}m ${secs}s`;
  if (mins > 0) return `${mins}m`;
  return `${secs}s`;
}

async function defaultSendEmail({ to, subject, text, html, HttpsError, fetchFn }) {
  const from = process.env.EMAIL_FROM || process.env.SMTP_FROM || process.env.SMTP_USER || "no-reply@totalsolution.app";
  const resendKey = process.env.RESEND_API_KEY || "";
  const sendgridKey = process.env.SENDGRID_API_KEY || "";
  const smtpService = process.env.SMTP_SERVICE || "";
  const host = process.env.SMTP_HOST || (smtpService.toLowerCase() === "gmail" ? "smtp.gmail.com" : "");
  const port = Number(process.env.SMTP_PORT || (smtpService.toLowerCase() === "gmail" ? 465 : 587));
  const user = process.env.SMTP_USER || "";
  const pass = process.env.SMTP_PASS || "";

  if (resendKey) {
    const fn = fetchFn || globalThis.fetch;
    if (typeof fn !== "function") {
      throw new HttpsError("failed-precondition", "HTTP fetch is unavailable for Resend email delivery.");
    }
    const resp = await fn("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${resendKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ from, to: [to], subject, text, html })
    });
    if (!resp.ok) {
      const errBody = await resp.text().catch(() => "");
      throw new HttpsError("unavailable", `Email delivery failed via Resend (${resp.status}). ${errBody}`.trim());
    }
    return;
  }

  if (sendgridKey) {
    const fn = fetchFn || globalThis.fetch;
    if (typeof fn !== "function") {
      throw new HttpsError("failed-precondition", "HTTP fetch is unavailable for SendGrid email delivery.");
    }
    const resp = await fn("https://api.sendgrid.com/v3/mail/send", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${sendgridKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        personalizations: [{ to: [{ email: to }] }],
        from: { email: from },
        subject,
        content: [
          { type: "text/plain", value: text },
          { type: "text/html", value: html }
        ]
      })
    });
    if (!resp.ok) {
      const errBody = await resp.text().catch(() => "");
      throw new HttpsError("unavailable", `Email delivery failed via SendGrid (${resp.status}). ${errBody}`.trim());
    }
    return;
  }

  if (!host || !user || !pass) {
    throw new HttpsError(
      "failed-precondition",
      "Email OTP delivery is not configured on backend. Configure RESEND_API_KEY, SENDGRID_API_KEY, or SMTP_HOST/SMTP_USER/SMTP_PASS secrets in Firebase Functions."
    );
  }
  let nodemailer;
  try {
    nodemailer = require("nodemailer");
  } catch (e) {
    throw new HttpsError("failed-precondition", "nodemailer dependency is not installed on Functions runtime.");
  }
  const transportConfig = smtpService
    ? { service: smtpService, auth: { user, pass } }
    : { host, port, secure: port === 465, auth: { user, pass } };
  const transporter = nodemailer.createTransport(transportConfig);
  await transporter.sendMail({ from, to, subject, text, html });
}

function nowEpoch(Timestamp) {
  return Timestamp && typeof Timestamp.now === "function"
    ? Timestamp.now().toMillis()
    : Date.now();
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
 * Issues a real time-limited (5-minute) 6-digit Email OTP with 60s resend cooldown and abuse blocking.
 * Stores ONLY SHA-256 hash in Firestore (never plaintext OTP).
 */
async function requestEmailOtpHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError, sendEmail } = deps;
  const data = (request && request.data) || {};

  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const purpose = typeof data.purpose === "string" ? data.purpose.trim().toUpperCase() : "";
  const allowedPurposes = [
    "ADMIN_SIGNUP",
    "ADMIN_FORGOT_PASSWORD",
    "TECHNICIAN_ADD",
    "TECHNICIAN_FIRST_LOGIN",
    "TECHNICIAN_FORGOT_PASSWORD"
  ];
  if (!isValidEmail(email)) {
    throw new HttpsError("invalid-argument", "Please enter a valid email address.");
  }
  if (!allowedPurposes.includes(purpose)) {
    throw new HttpsError("invalid-argument", "Unsupported OTP purpose.");
  }

  if (purpose === "ADMIN_SIGNUP" || purpose === "TECHNICIAN_ADD") {
    let exists = false;
    if (auth && typeof auth.getUserByEmail === "function") {
      try {
        await auth.getUserByEmail(email);
        exists = true;
      } catch (err) {
        if (err && err.code && err.code !== "auth/user-not-found") {
          throw new HttpsError("internal", "Unable to check duplicate email registration.");
        }
      }
    }
    if (!exists && purpose === "ADMIN_SIGNUP") {
      const [wsSnap, adminSnap] = await Promise.all([
        db.collection("workspaces").where("ownerEmail", "==", email).limit(1).get(),
        db.collection("admins").where("email", "==", email).limit(1).get()
      ]);
      if (!wsSnap.empty || !adminSnap.empty) {
        exists = true;
      }
    }
    if (exists) {
      throw new HttpsError("already-exists", "An account with this email address already exists.");
    }
  }

  const createdAtMillis = nowEpoch(Timestamp);
  const rateKey = hashRateLimitKey(email, purpose);
  const rateRef = db.collection("otpRateLimits").doc(rateKey);
  const rateSnap = await rateRef.get();
  const rateData = rateSnap.exists ? (rateSnap.data() || {}) : {};

  const blockedUntilMillis = Number(rateData.blockedUntilMillis || 0);
  if (blockedUntilMillis > createdAtMillis) {
    throw new HttpsError(
      "resource-exhausted",
      `Too many OTP attempts. Temporarily blocked for ${formatBlockRemaining(blockedUntilMillis - createdAtMillis)}.`
    );
  }

  const lastSentAtMillis = Number(rateData.lastSentAtMillis || 0);
  if (lastSentAtMillis > 0 && createdAtMillis - lastSentAtMillis < OTP_RESEND_COOLDOWN_MS) {
    const waitSec = Math.max(1, Math.ceil((OTP_RESEND_COOLDOWN_MS - (createdAtMillis - lastSentAtMillis)) / 1000));
    throw new HttpsError(
      "resource-exhausted",
      `Please wait ${waitSec}s before requesting another OTP.`
    );
  }

  const prevWindowStart = Number(rateData.requestWindowStartMillis || 0);
  const inWindow = prevWindowStart > 0 && (createdAtMillis - prevWindowStart) <= OTP_ABUSE_WINDOW_MS;
  const nextWindowStart = inWindow ? prevWindowStart : createdAtMillis;
  const nextCount = inWindow ? (Number(rateData.requestCountInWindow || 0) + 1) : 1;
  if (nextCount > OTP_MAX_REQUESTS_PER_WINDOW) {
    const newBlockUntil = createdAtMillis + OTP_BLOCK_DURATION_MS;
    await rateRef.set({
      email,
      purpose,
      lastSentAtMillis,
      requestWindowStartMillis: nextWindowStart,
      requestCountInWindow: nextCount,
      blockedUntilMillis: newBlockUntil,
      activeChallengeId: rateData.activeChallengeId || ""
    }, { merge: true });
    throw new HttpsError(
      "resource-exhausted",
      "Too many OTP requests. Temporarily blocked for 15 minutes."
    );
  }

  // Supersede any previous active unverified challenge for this email & purpose
  const prevChallengeId = typeof rateData.activeChallengeId === "string" ? rateData.activeChallengeId : "";
  if (prevChallengeId && isValidFirestoreId(prevChallengeId)) {
    try {
      const prevRef = db.collection("otpChallenges").doc(prevChallengeId);
      const prevSnap = await prevRef.get();
      if (prevSnap.exists) {
        const prev = prevSnap.data() || {};
        if (!prev.used && !prev.verified) {
          await prevRef.update({ used: true, superseded: true, supersededAtMillis: createdAtMillis });
        }
      }
    } catch (_) {
      // Best-effort cleanup of previous active challenge.
    }
  }

  const otpCode = String(crypto.randomInt(100000, 1000000));
  const challengeId = `otp_${crypto.randomBytes(12).toString("hex")}`;
  const expiresAtMillis = createdAtMillis + OTP_TTL_MS;
  const resendAvailableAtMillis = createdAtMillis + OTP_RESEND_COOLDOWN_MS;
  const otpHash = hashOtpCode(challengeId, email, purpose, otpCode);
  const challengeRef = db.collection("otpChallenges").doc(challengeId);

  await challengeRef.set({
    challengeId,
    email,
    purpose,
    otpHash,
    attemptsLeft: OTP_MAX_VERIFY_ATTEMPTS,
    verified: false,
    used: false,
    verificationTokenHash: "",
    createdAtMillis,
    expiresAtMillis,
    createdAt: FieldValue ? FieldValue.serverTimestamp() : createdAtMillis
  });

  const mailer = sendEmail || ((mail) => defaultSendEmail({ ...mail, HttpsError }));
  try {
    await mailer({
      to: email,
      subject: `${OFFICIAL_APP_NAME} — Email Verification OTP`,
      text: `Your ${OFFICIAL_APP_NAME} verification OTP for ${purpose.replace(/_/g, " ")} is ${otpCode}. This OTP expires in 5 minutes. Do not share it with anyone.`,
      html: `<p>Your <strong>${OFFICIAL_APP_NAME}</strong> verification OTP is:</p><h2>${otpCode}</h2><p>Valid for 5 minutes. Do not share this code.</p>`
    });
  } catch (mailErr) {
    try {
      await challengeRef.delete();
    } catch (_) {
      // Best-effort cleanup of unsent challenge.
    }
    throw mailErr;
  }

  await rateRef.set({
    email,
    purpose,
    lastSentAtMillis: createdAtMillis,
    requestWindowStartMillis: nextWindowStart,
    requestCountInWindow: nextCount,
    blockedUntilMillis: 0,
    activeChallengeId: challengeId,
    updatedAt: FieldValue ? FieldValue.serverTimestamp() : createdAtMillis
  }, { merge: true });

  return {
    challengeId,
    email,
    purpose,
    expiresAtMillis,
    resendAvailableAtMillis,
    maskedEmail: maskEmail(email)
  };
}

/**
 * Verifies a 6-digit Email OTP against stored SHA-256 hash & issues a one-time verificationToken.
 */
async function verifyEmailOtpHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};

  const challengeId = typeof data.challengeId === "string" ? data.challengeId.trim() : "";
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const purpose = typeof data.purpose === "string" ? data.purpose.trim().toUpperCase() : "";
  const otp = typeof data.otp === "string" ? data.otp.trim() : "";

  if (!isValidFirestoreId(challengeId) || !isValidEmail(email) || !/^\d{6}$/.test(otp)) {
    throw new HttpsError("invalid-argument", "Enter a valid 6-digit Email OTP code.");
  }

  const currentMillis = nowEpoch(Timestamp);
  const ref = db.collection("otpChallenges").doc(challengeId);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "OTP verification session not found. Please request a new OTP.");
  }
  const ch = snap.data() || {};

  const ratePurpose = ch.purpose || purpose;
  const rateRef = db.collection("otpRateLimits").doc(hashRateLimitKey(email, ratePurpose));
  const rateSnap = await rateRef.get();
  if (rateSnap.exists) {
    const blockedUntilMillis = Number((rateSnap.data() || {}).blockedUntilMillis || 0);
    if (blockedUntilMillis > currentMillis) {
      throw new HttpsError(
        "resource-exhausted",
        `Too many failed OTP attempts. Temporarily blocked for ${formatBlockRemaining(blockedUntilMillis - currentMillis)}.`
      );
    }
  }

  if (ch.used) {
    throw new HttpsError("failed-precondition", "This OTP is no longer active. Please request a new OTP.");
  }
  if (ch.email !== email || (purpose && ch.purpose !== purpose)) {
    throw new HttpsError("permission-denied", "OTP does not match this email or operation.");
  }
  if (Number(ch.expiresAtMillis || 0) < currentMillis) {
    throw new HttpsError("deadline-exceeded", "OTP has expired after 5 minutes. Please request a new OTP.");
  }
  const attemptsLeft = Number(ch.attemptsLeft || 0);
  if (attemptsLeft <= 0) {
    throw new HttpsError("resource-exhausted", "Too many wrong OTP attempts. Temporarily blocked for 15 minutes.");
  }

  const suppliedHash = hashOtpCode(challengeId, email, ch.purpose, otp);
  if (!timingSafeHashEqual(ch.otpHash, suppliedHash)) {
    const remaining = attemptsLeft - 1;
    if (remaining <= 0) {
      await ref.update({ attemptsLeft: 0, used: true, blocked: true });
      await rateRef.set({
        email,
        purpose: ch.purpose,
        blockedUntilMillis: currentMillis + OTP_BLOCK_DURATION_MS,
        updatedAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis
      }, { merge: true });
      throw new HttpsError(
        "resource-exhausted",
        "Too many wrong OTP attempts (5/5). Temporarily blocked for 15 minutes."
      );
    }
    await ref.update({ attemptsLeft: remaining });
    throw new HttpsError(
      "permission-denied",
      `Invalid OTP code. ${remaining} attempt${remaining === 1 ? "" : "s"} remaining.`
    );
  }

  const verificationToken = crypto.randomBytes(24).toString("hex");
  const verificationTokenHash = crypto.createHash("sha256").update(verificationToken).digest("hex");

  await ref.update({
    verified: true,
    verifiedAtMillis: currentMillis,
    verificationTokenHash
  });

  if (rateSnap.exists) {
    await rateRef.set({
      blockedUntilMillis: 0,
      requestCountInWindow: 0,
      updatedAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis
    }, { merge: true });
  }

  return {
    verified: true,
    challengeId,
    email,
    purpose: ch.purpose,
    verificationToken
  };
}

async function consumeVerifiedOtpChallenge({ db, challengeId, verificationToken, email, expectedPurpose, Timestamp, HttpsError }) {
  if (!isValidFirestoreId(challengeId) || typeof verificationToken !== "string" || verificationToken.length < 16) {
    throw new HttpsError("permission-denied", "Verified Email OTP token is required.");
  }
  const ref = db.collection("otpChallenges").doc(challengeId);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("permission-denied", "OTP verification record missing.");
  }
  const ch = snap.data() || {};
  const currentMillis = nowEpoch(Timestamp);
  if (!ch.verified || ch.used) {
    throw new HttpsError("permission-denied", "Email OTP is unverified or already consumed.");
  }
  if (ch.email !== email.toLowerCase() || ch.purpose !== expectedPurpose) {
    throw new HttpsError("permission-denied", "Email OTP does not match email or operation.");
  }
  const completionDeadline = Math.max(
    Number(ch.expiresAtMillis || 0),
    Number(ch.verifiedAtMillis || 0) + 10 * 60 * 1000
  );
  if (completionDeadline < currentMillis) {
    throw new HttpsError("deadline-exceeded", "Email OTP verification expired. Please verify again.");
  }
  const tokenHash = crypto.createHash("sha256").update(verificationToken).digest("hex");
  if (!timingSafeHashEqual(ch.verificationTokenHash, tokenHash)) {
    throw new HttpsError("permission-denied", "Invalid Email OTP verification token.");
  }
  return ref;
}

/**
 * Part 3: Secure Admin Sign Up with permanently locked company name TOTAL SOLUTION,
 * real verified Email OTP, Two-Factor Authentication password (scrypt hash),
 * unique Admin UID & Company ID.
 */
async function registerCompanyAdminHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};

  const requestedCompany = typeof data.companyName === "string" ? data.companyName.trim() : OFFICIAL_COMPANY_NAME;
  if (requestedCompany && requestedCompany !== OFFICIAL_COMPANY_NAME) {
    throw new HttpsError("invalid-argument", `Official company name is locked as ${OFFICIAL_COMPANY_NAME}.`);
  }

  const adminName = typeof data.adminName === "string" ? data.adminName.trim() : "";
  const mobile = typeof data.mobile === "string" ? data.mobile.trim() : "";
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const password = typeof data.password === "string" ? data.password : "";
  const confirmPassword = typeof data.confirmPassword === "string" ? data.confirmPassword : "";
  const twoFactorPassword = typeof data.twoFactorPassword === "string" ? data.twoFactorPassword : "";
  const confirmTwoFactorPassword = typeof data.confirmTwoFactorPassword === "string" ? data.confirmTwoFactorPassword : "";
  const otpChallengeId = typeof data.otpChallengeId === "string" ? data.otpChallengeId.trim() : "";
  const otpVerificationToken = typeof data.otpVerificationToken === "string" ? data.otpVerificationToken.trim() : "";

  if (adminName.length < 2 || adminName.length > 100) {
    throw new HttpsError("invalid-argument", "Admin name must be between 2 and 100 characters.");
  }
  if (mobile.replace(/\D/g, "").length < 7) {
    throw new HttpsError("invalid-argument", "Enter a valid Admin mobile number.");
  }
  if (!isValidEmail(email)) {
    throw new HttpsError("invalid-argument", "Enter a valid Admin email address.");
  }
  const passErr = validateStrongPassword(password);
  if (passErr) {
    throw new HttpsError("invalid-argument", passErr);
  }
  if (password !== confirmPassword) {
    throw new HttpsError("invalid-argument", "Password and Confirm Password do not match.");
  }
  if (twoFactorPassword.length < 4 || twoFactorPassword.length > 64) {
    throw new HttpsError("invalid-argument", "Two-Factor Authentication Password must be 4 to 64 characters.");
  }
  if (twoFactorPassword !== confirmTwoFactorPassword) {
    throw new HttpsError("invalid-argument", "Two-Factor Authentication passwords do not match.");
  }
  if (twoFactorPassword === password) {
    throw new HttpsError("invalid-argument", "Two-Factor Authentication Password must differ from your login password.");
  }

  const otpRef = await consumeVerifiedOtpChallenge({
    db,
    challengeId: otpChallengeId,
    verificationToken: otpVerificationToken,
    email,
    expectedPurpose: "ADMIN_SIGNUP",
    Timestamp,
    HttpsError
  });

  // Prevent duplicate Admin registration for the same email
  const [dupWsSnap, dupAdminSnap] = await Promise.all([
    db.collection("workspaces").where("ownerEmail", "==", email).limit(1).get(),
    db.collection("admins").where("email", "==", email).limit(1).get()
  ]);
  if (!dupWsSnap.empty || !dupAdminSnap.empty) {
    throw new HttpsError("already-exists", "An Admin account is already registered with this email.");
  }

  let userRecord;
  try {
    userRecord = await auth.createUser({
      email,
      password,
      displayName: adminName,
      emailVerified: true,
      disabled: false
    });
  } catch (err) {
    if (err && err.code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "An account with this email already exists.");
    }
    throw new HttpsError("internal", `Unable to create Admin account: ${err.message || "unknown error"}`);
  }

  const adminUid = userRecord.uid;
  const companyId = `comp_${crypto.randomBytes(8).toString("hex")}`;
  const workspaceId = companyId;
  const techShareCode = `TS_TECH_${crypto.randomBytes(4).toString("hex").toUpperCase()}`;
  const superShareCode = `TS_SA_${crypto.randomBytes(4).toString("hex").toUpperCase()}`;
  const username = deriveSafeUsername(data.username, email, adminName);
  const twoFactorHash = hashScryptSecret(twoFactorPassword);

  try {
    if (typeof auth.setCustomUserClaims === "function") {
      await auth.setCustomUserClaims(adminUid, {
        role: "admin",
        workspaceId,
        companyId
      });
    }

    const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowEpoch(Timestamp);
    const batch = db.batch();

    batch.set(db.collection("admins").doc(adminUid), {
      userId: adminUid,
      email,
      createdAt: nowTs,
      updatedAt: nowTs
    });

    batch.set(db.collection("secondFactorSecrets").doc(adminUid), {
      uid: adminUid,
      twoFactorHash,
      updatedAt: nowTs
    });

    batch.set(db.collection("companies").doc(companyId), {
      companyId,
      companyName: OFFICIAL_COMPANY_NAME,
      officialAppName: OFFICIAL_APP_NAME,
      ownerUid: adminUid,
      ownerEmail: email,
      mobile,
      createdAt: nowTs,
      updatedAt: nowTs
    });

    batch.set(db.collection("workspaces").doc(workspaceId), {
      workspaceId,
      companyId,
      ownerUid: adminUid,
      ownerEmail: email,
      techShareCode,
      superShareCode,
      companyName: OFFICIAL_COMPANY_NAME,
      mobile,
      email,
      address: "B1, Santa Anna Holiday Homes, Naika Vaddo, Calangute, North Goa 403516.",
      upiId: "",
      payeeName: adminName,
      logoBase64: "",
      authorisedSignatureBase64: "",
      fallbackQrBase64: "",
      headerNote: `Welcome to ${OFFICIAL_COMPANY_NAME} — CCTV Security, Sales, Installation & Support`,
      voiceLogin: `Welcome to ${OFFICIAL_APP_NAME}. Select your account and sign in.`,
      voicePostLogin: `Login successful. Welcome to ${OFFICIAL_COMPANY_NAME}.`,
      voiceBillStart: "Generating bill. Please verify customer summary.",
      voiceBillDone: `Bill generated. Total amount is {amount} rupees. Thank you for choosing ${OFFICIAL_COMPANY_NAME}.`,
      voiceModelId: "preset_clear",
      memberUids: [adminUid],
      createdAt: nowTs,
      updatedAt: nowTs
    });

    batch.set(db.collection("workspaces").doc(workspaceId).collection("members").doc(adminUid), {
      userId: adminUid,
      uid: adminUid,
      email,
      displayName: adminName,
      name: adminName,
      username,
      phone: mobile,
      role: "admin",
      workspaceId,
      companyId,
      inviteCode: "OWNER",
      loginEnabled: true,
      mustChangePassword: false,
      authProvider: "password",
      createdAt: nowTs,
      updatedAt: nowTs
    });

    batch.set(db.collection("workspaces").doc(workspaceId).collection("accounts").doc(username), {
      username,
      displayName: adminName,
      passwordHash: "FIREBASE_EMAIL_AUTH_ONLY",
      role: "admin",
      phone: mobile,
      email,
      uid: adminUid,
      companyId,
      loginEnabled: true,
      photoBase64: "",
      updatedAt: nowTs
    });

    batch.update(otpRef, { used: true, usedAtMillis: nowEpoch(Timestamp) });

    await batch.commit();
  } catch (dbErr) {
    try {
      await auth.deleteUser(adminUid);
    } catch (_) {
      // Best effort rollback.
    }
    throw new HttpsError("internal", `Failed to register Admin company workspace: ${dbErr.message || "unknown error"}`);
  }

  return {
    uid: adminUid,
    companyId,
    workspaceId,
    email,
    username,
    adminName,
    companyName: OFFICIAL_COMPANY_NAME,
    role: "admin"
  };
}

/**
 * Verifies the Admin's server-hashed Two-Factor Authentication Password.
 */
async function verifyAdminSecondFactorHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const data = (request && request.data) || {};
  const twoFactorPassword = typeof data.twoFactorPassword === "string" ? data.twoFactorPassword : "";
  if (!twoFactorPassword) {
    throw new HttpsError("invalid-argument", "Two-Factor Authentication Password is required.");
  }

  const secretSnap = await db.collection("secondFactorSecrets").doc(authCtx.uid).get();
  if (!secretSnap.exists) {
    throw new HttpsError("failed-precondition", "Two-Factor Authentication secret is not configured for this account.");
  }
  const secretData = secretSnap.data() || {};
  if (!verifyScryptSecret(twoFactorPassword, secretData.twoFactorHash)) {
    throw new HttpsError("permission-denied", "Incorrect Two-Factor Authentication Password.");
  }

  await secretSnap.ref.update({
    lastVerifiedAt: FieldValue ? FieldValue.serverTimestamp() : nowEpoch(Timestamp)
  });

  return { verified: true, uid: authCtx.uid };
}

/**
 * Resets Firebase Auth password after verifying an Email OTP challenge.
 */
async function resetPasswordWithOtpHandler(request, deps) {
  const { auth, db, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};

  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const purpose = typeof data.purpose === "string" ? data.purpose.trim().toUpperCase() : "ADMIN_FORGOT_PASSWORD";
  const otpChallengeId = typeof data.otpChallengeId === "string" ? data.otpChallengeId.trim() : "";
  const otpVerificationToken = typeof data.otpVerificationToken === "string" ? data.otpVerificationToken.trim() : "";
  const newPassword = typeof data.newPassword === "string" ? data.newPassword : "";
  const confirmPassword = typeof data.confirmPassword === "string" ? data.confirmPassword : "";

  if (!["ADMIN_FORGOT_PASSWORD", "TECHNICIAN_FORGOT_PASSWORD"].includes(purpose)) {
    throw new HttpsError("invalid-argument", "Invalid password reset purpose.");
  }
  if (!isValidEmail(email)) {
    throw new HttpsError("invalid-argument", "Enter a valid registered email address.");
  }
  const passErr = validateStrongPassword(newPassword);
  if (passErr) {
    throw new HttpsError("invalid-argument", passErr);
  }
  if (newPassword !== confirmPassword) {
    throw new HttpsError("invalid-argument", "New Password and Confirm Password do not match.");
  }

  const otpRef = await consumeVerifiedOtpChallenge({
    db,
    challengeId: otpChallengeId,
    verificationToken: otpVerificationToken,
    email,
    expectedPurpose: purpose,
    Timestamp,
    HttpsError
  });

  let userRecord;
  try {
    userRecord = await auth.getUserByEmail(email);
  } catch (_) {
    throw new HttpsError("not-found", "No registered user found with this email.");
  }
  if (userRecord.disabled) {
    throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
  }

  await auth.updateUser(userRecord.uid, { password: newPassword });
  await otpRef.update({ used: true, usedAtMillis: nowEpoch(Timestamp) });

  return {
    reset: true,
    email,
    uid: userRecord.uid
  };
}

/**
 * Creates a Technician Firebase Auth account via Admin SDK, binds to Admin's companyId,
 * generates unique techId, optionally enforces verified Email OTP when otpChallengeId is given,
 * and saves non-secret metadata in Firestore.
 */
async function createTechnicianAccountHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};

  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const name = typeof data.name === "string" ? data.name.trim() : "";
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const initialPassword = typeof data.initialPassword === "string" ? data.initialPassword : "";
  const phone = typeof data.phone === "string" ? data.phone.trim().slice(0, 32) : "";
  const otpChallengeId = typeof data.otpChallengeId === "string" ? data.otpChallengeId.trim() : "";
  const otpVerificationToken = typeof data.otpVerificationToken === "string" ? data.otpVerificationToken.trim() : "";
  const requireOtpVerification = Boolean(data.requireOtpVerification || otpChallengeId);

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

  let otpRef = null;
  if (requireOtpVerification) {
    otpRef = await consumeVerifiedOtpChallenge({
      db,
      challengeId: otpChallengeId,
      verificationToken: otpVerificationToken,
      email,
      expectedPurpose: "TECHNICIAN_ADD",
      Timestamp,
      HttpsError
    });
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
  const techId = `TECH-${crypto.randomBytes(4).toString("hex").toUpperCase()}`;

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
        workspaceId,
        companyId: workspaceId,
        techId,
        loginEnabled: true
      });
    }

    const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowEpoch(Timestamp);
    const batch = db.batch();

    batch.update(wsRef, {
      memberUids: FieldValue.arrayUnion(techUid),
      updatedAt: nowTs
    });

    // Never store plaintext password or plaintext activation passkey in Firestore.
    batch.set(wsRef.collection("members").doc(techUid), {
      userId: techUid,
      uid: techUid,
      techId,
      email,
      displayName: name,
      name,
      username,
      phone,
      role: "technician",
      workspaceId,
      companyId: workspaceId,
      inviteCode: String(wsData.techShareCode || ""),
      mustChangePassword: true,
      firstLoginCompleted: false,
      loginEnabled: true,
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
      email,
      uid: techUid,
      techId,
      companyId: workspaceId,
      loginEnabled: true,
      photoBase64: "",
      updatedAt: nowTs
    }, { merge: true });

    if (otpRef) {
      batch.update(otpRef, { used: true, usedAtMillis: nowEpoch(Timestamp) });
    }

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
    techId,
    email,
    name,
    username,
    phone,
    role: "technician",
    workspaceId,
    companyId: workspaceId,
    loginEnabled: true,
    mustChangePassword: true,
    activationPasskey
  };
}

/**
 * Verifies a Technician's authorized email + Admin-issued Activation Passkey on first launch
 * before showing Login screen.
 */
async function verifyTechnicianActivationHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
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
  } catch (_) {
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
  if (memberData.loginEnabled === false || userRecord.disabled) {
    throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
  }

  const storedHash = String(memberData.activationPasskeyHash || "");
  if (!timingSafeHashEqual(storedHash, suppliedHash)) {
    throw new HttpsError(
      "permission-denied",
      "Email is not authorized or Activation Passkey is incorrect."
    );
  }

  if (!memberData.activatedAt) {
    const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowEpoch(Timestamp);
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
    companyId: wsId,
    techId: String(memberData.techId || ""),
    email,
    phone: String(memberData.phone || ""),
    username,
    name,
    companyName: String(wsData.companyName || OFFICIAL_COMPANY_NAME),
    mustChangePassword: Boolean(memberData.mustChangePassword)
  };
}

/**
 * Part 6: Technician First-Time Onboarding completion (mandatory photo, custom username,
 * new strong password via Admin SDK or signed-in Auth, unique Technician ID).
 */
async function completeTechnicianFirstLoginHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }

  const data = (request && request.data) || {};
  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const requestedUsername = typeof data.username === "string" ? data.username.trim() : "";
  const newPassword = typeof data.newPassword === "string" ? data.newPassword : "";
  const photoBase64 = typeof data.photoBase64 === "string" ? data.photoBase64.trim() : "";

  if (!isValidFirestoreId(workspaceId)) {
    throw new HttpsError("invalid-argument", "Invalid workspaceId.");
  }
  if (photoBase64.length < 12) {
    throw new HttpsError("invalid-argument", "Mandatory Technician profile photo is required.");
  }
  const passErr = validateStrongPassword(newPassword);
  if (passErr) {
    throw new HttpsError("invalid-argument", passErr);
  }

  const wsRef = db.collection("workspaces").doc(workspaceId);
  const memberRef = wsRef.collection("members").doc(authCtx.uid);
  const memberSnap = await memberRef.get();
  if (!memberSnap.exists) {
    throw new HttpsError("not-found", "Technician membership record not found.");
  }
  const memberData = memberSnap.data() || {};
  if (String(memberData.role || "").toLowerCase() !== "technician") {
    throw new HttpsError("permission-denied", "Only Technician accounts can complete first-time onboarding.");
  }
  if (memberData.loginEnabled === false) {
    throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
  }

  const finalUsername = deriveSafeUsername(requestedUsername, memberData.email, memberData.name);
  const prevUsername = String(memberData.username || "");
  const techId = String(memberData.techId || `TECH-${crypto.randomBytes(4).toString("hex").toUpperCase()}`);

  if (finalUsername !== prevUsername) {
    const dup = await wsRef.collection("accounts").doc(finalUsername).get();
    if (dup.exists) {
      throw new HttpsError("already-exists", `Username @${finalUsername} is already taken.`);
    }
  }

  await auth.updateUser(authCtx.uid, { password: newPassword });

  const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowEpoch(Timestamp);
  const batch = db.batch();
  batch.update(memberRef, {
    username: finalUsername,
    techId,
    mustChangePassword: false,
    firstLoginCompleted: true,
    passwordChangedAt: nowTs,
    updatedAt: nowTs
  });

  if (prevUsername && prevUsername !== finalUsername) {
    batch.delete(wsRef.collection("accounts").doc(prevUsername));
  }
  batch.set(wsRef.collection("accounts").doc(finalUsername), {
    username: finalUsername,
    displayName: String(memberData.name || memberData.displayName || finalUsername),
    passwordHash: "FIREBASE_EMAIL_AUTH_ONLY",
    role: "technician",
    phone: String(memberData.phone || ""),
    email: String(memberData.email || ""),
    uid: authCtx.uid,
    techId,
    companyId: workspaceId,
    loginEnabled: true,
    photoBase64,
    updatedAt: nowTs
  }, { merge: true });

  await batch.commit();

  return {
    uid: authCtx.uid,
    techId,
    workspaceId,
    username: finalUsername,
    mustChangePassword: false,
    firstLoginCompleted: true
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

/**
 * Part 18: Admin ONLY Technician Login Control (ENABLED / DISABLED).
 * Revokes Firebase refresh tokens, sets Auth disabled flag, updates member/account state,
 * preserves all existing technician records, and writes an audit log entry.
 */
async function setTechnicianLoginStatusHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};

  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const technicianUid = typeof data.technicianUid === "string" ? data.technicianUid.trim() : "";
  const enabled = Boolean(data.enabled);

  if (!isValidFirestoreId(technicianUid)) {
    throw new HttpsError("invalid-argument", "Valid technicianUid is required.");
  }

  const { wsRef, callerUid, callerEmail } = await assertAuthorizedWorkspaceAdmin({
    authCtx: request.auth,
    db,
    workspaceId,
    HttpsError
  });

  const memberRef = wsRef.collection("members").doc(technicianUid);
  const memberSnap = await memberRef.get();
  if (!memberSnap.exists) {
    throw new HttpsError("not-found", "Technician not found in this company.");
  }
  const memberData = memberSnap.data() || {};
  if (String(memberData.role || "").toLowerCase() !== "technician") {
    throw new HttpsError("permission-denied", "Login Control applies only to Technician accounts.");
  }
  if (String(memberData.workspaceId || workspaceId) !== workspaceId) {
    throw new HttpsError("permission-denied", "Cross-company Technician access is prohibited.");
  }

  if (auth && typeof auth.updateUser === "function") {
    await auth.updateUser(technicianUid, { disabled: !enabled });
  }
  if (!enabled && auth && typeof auth.revokeRefreshTokens === "function") {
    await auth.revokeRefreshTokens(technicianUid);
  }
  if (auth && typeof auth.setCustomUserClaims === "function") {
    await auth.setCustomUserClaims(technicianUid, {
      role: "technician",
      workspaceId,
      companyId: workspaceId,
      techId: String(memberData.techId || ""),
      loginEnabled: enabled
    });
  }

  const nowMillis = nowEpoch(Timestamp);
  const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowMillis;
  const batch = db.batch();
  batch.update(memberRef, {
    loginEnabled: enabled,
    statusUpdatedAt: nowTs,
    statusUpdatedByUid: callerUid,
    updatedAt: nowTs
  });

  const username = String(memberData.username || "");
  if (username) {
    const accRef = wsRef.collection("accounts").doc(username);
    batch.set(accRef, {
      username,
      loginEnabled: enabled,
      updatedAt: nowTs
    }, { merge: true });
  }

  const logId = `log_tech_status_${nowMillis}_${technicianUid.slice(0, 6)}`;
  batch.set(wsRef.collection("auditLogs").doc(logId), {
    logId,
    action: enabled ? "TECHNICIAN_LOGIN_ENABLED" : "TECHNICIAN_LOGIN_DISABLED",
    entityType: "TECHNICIAN",
    entityId: technicianUid,
    title: String(memberData.name || memberData.displayName || username || technicianUid),
    actorUsername: callerEmail || callerUid,
    actorRole: "admin",
    details: enabled ? "Admin enabled Technician login" : "Admin disabled Technician login and revoked active tokens",
    createdAt: nowMillis,
    serverCreatedAt: nowTs
  });

  await batch.commit();

  return {
    technicianUid,
    workspaceId,
    loginEnabled: enabled
  };
}

/**
 * Checks if a user's workspace login is active (blocks disabled technicians immediately).
 */
async function verifyMemberLoginAccessHandler(request, deps) {
  const { auth, db, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const data = (request && request.data) || {};
  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";

  if (auth && typeof auth.getUser === "function") {
    const u = await auth.getUser(authCtx.uid);
    if (u.disabled) {
      throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
    }
  }

  if (workspaceId && isValidFirestoreId(workspaceId)) {
    const memberSnap = await db.collection("workspaces").doc(workspaceId).collection("members").doc(authCtx.uid).get();
    if (memberSnap.exists) {
      const m = memberSnap.data() || {};
      if (m.loginEnabled === false) {
        throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
      }
      return {
        allowed: true,
        role: String(m.role || ""),
        workspaceId,
        techId: String(m.techId || ""),
        loginEnabled: true
      };
    }
  }
  return { allowed: true, loginEnabled: true };
}

/**
 * Part 8: Real FIDO2/WebAuthn Passkey Registration & Authentication handlers.
 */
async function beginPasskeyRegistrationHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Sign in before registering a Passkey.");
  }
  const data = (request && request.data) || {};
  const workspaceId = typeof data.workspaceId === "string" ? data.workspaceId.trim() : "";
  const rpId = (typeof data.rpId === "string" && data.rpId.trim()) || process.env.WEBAUTHN_RP_ID || DEFAULT_WEBAUTHN_RP_ID;

  const email = String(data.email || (authCtx.token && authCtx.token.email) || "").trim().toLowerCase();
  const displayName = String(data.displayName || email || authCtx.uid).trim();
  const role = String(data.role || "admin").trim().toLowerCase();

  if (workspaceId && isValidFirestoreId(workspaceId)) {
    const mSnap = await db.collection("workspaces").doc(workspaceId).collection("members").doc(authCtx.uid).get();
    if (mSnap.exists && mSnap.data().loginEnabled === false) {
      throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
    }
  }

  const challenge = base64UrlEncode(crypto.randomBytes(32));
  const challengeId = `pkreg_${crypto.randomBytes(12).toString("hex")}`;
  const currentMillis = nowEpoch(Timestamp);

  await db.collection("passkeyChallenges").doc(challengeId).set({
    challengeId,
    type: "registration",
    challenge,
    rpId,
    uid: authCtx.uid,
    email,
    role,
    workspaceId,
    used: false,
    createdAtMillis: currentMillis,
    expiresAtMillis: currentMillis + WEBAUTHN_CHALLENGE_TTL_MS,
    createdAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis
  });

  const options = {
    challenge,
    rp: {
      name: OFFICIAL_APP_NAME,
      id: rpId
    },
    user: {
      id: base64UrlEncode(Buffer.from(authCtx.uid, "utf8")),
      name: email || authCtx.uid,
      displayName
    },
    pubKeyCredParams: [
      { type: "public-key", alg: -7 },
      { type: "public-key", alg: -257 }
    ],
    timeout: 60000,
    attestation: "none",
    authenticatorSelection: {
      residentKey: "preferred",
      userVerification: "required"
    }
  };

  return {
    challengeId,
    rpId,
    requestJson: JSON.stringify(options)
  };
}

async function finishPasskeyRegistrationHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Sign in before saving a Passkey.");
  }
  const data = (request && request.data) || {};
  const challengeId = typeof data.challengeId === "string" ? data.challengeId.trim() : "";
  const registrationResponseJson = data.registrationResponseJson;

  if (!isValidFirestoreId(challengeId) || !registrationResponseJson) {
    throw new HttpsError("invalid-argument", "challengeId and registrationResponseJson are required.");
  }

  const chRef = db.collection("passkeyChallenges").doc(challengeId);
  const chSnap = await chRef.get();
  if (!chSnap.exists) {
    throw new HttpsError("not-found", "Passkey registration challenge not found.");
  }
  const ch = chSnap.data() || {};
  const currentMillis = nowEpoch(Timestamp);
  if (ch.used || ch.type !== "registration" || ch.uid !== authCtx.uid) {
    throw new HttpsError("permission-denied", "Invalid passkey registration challenge.");
  }
  if (Number(ch.expiresAtMillis || 0) < currentMillis) {
    throw new HttpsError("deadline-exceeded", "Passkey registration challenge expired.");
  }

  let verifiedCred;
  try {
    verifiedCred = verifyRegistrationResponse({
      responseJson: registrationResponseJson,
      expectedChallenge: ch.challenge,
      rpId: ch.rpId,
      allowedApkOrigins: (process.env.WEBAUTHN_ALLOWED_ORIGINS || "").split(",").map((s) => s.trim()).filter(Boolean)
    });
  } catch (err) {
    throw new HttpsError("permission-denied", `Passkey verification failed: ${err.message}`);
  }

  const batch = db.batch();
  batch.update(chRef, { used: true, usedAtMillis: currentMillis });
  // Store ONLY public key metadata — never private keys.
  batch.set(db.collection("passkeyCredentials").doc(verifiedCred.credentialId), {
    credentialId: verifiedCred.credentialId,
    uid: authCtx.uid,
    email: ch.email,
    role: ch.role,
    workspaceId: ch.workspaceId || "",
    rpId: ch.rpId,
    alg: verifiedCred.alg,
    publicKeyPem: verifiedCred.publicKeyPem,
    signCount: verifiedCred.signCount,
    origin: verifiedCred.origin,
    createdAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis,
    updatedAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis
  });
  await batch.commit();

  return {
    registered: true,
    credentialId: verifiedCred.credentialId,
    uid: authCtx.uid
  };
}

async function beginPasskeyAuthenticationHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};
  const email = typeof data.email === "string" ? data.email.trim().toLowerCase() : "";
  const expectedRole = typeof data.expectedRole === "string" ? data.expectedRole.trim().toLowerCase() : "";
  const rpId = (typeof data.rpId === "string" && data.rpId.trim()) || process.env.WEBAUTHN_RP_ID || DEFAULT_WEBAUTHN_RP_ID;

  const allowCredentials = [];
  if (email && isValidEmail(email)) {
    if (auth && typeof auth.getUserByEmail === "function") {
      try {
        const u = await auth.getUserByEmail(email);
        if (u.disabled) {
          throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
        }
      } catch (err) {
        if (err instanceof HttpsError || err.code === "permission-denied") throw err;
      }
    }
    const credsSnap = await db.collection("passkeyCredentials").where("email", "==", email).get();
    for (const d of (credsSnap.docs || [])) {
      const c = d.data() || {};
      if (!expectedRole || !c.role || c.role === expectedRole) {
        allowCredentials.push({ type: "public-key", id: c.credentialId || d.id });
      }
    }
  }

  const challenge = base64UrlEncode(crypto.randomBytes(32));
  const challengeId = `pkauth_${crypto.randomBytes(12).toString("hex")}`;
  const currentMillis = nowEpoch(Timestamp);

  await db.collection("passkeyChallenges").doc(challengeId).set({
    challengeId,
    type: "authentication",
    challenge,
    rpId,
    email,
    expectedRole,
    used: false,
    createdAtMillis: currentMillis,
    expiresAtMillis: currentMillis + WEBAUTHN_CHALLENGE_TTL_MS,
    createdAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis
  });

  const options = {
    challenge,
    rpId,
    timeout: 60000,
    userVerification: "required",
    ...(allowCredentials.length > 0 ? { allowCredentials } : {})
  };

  return {
    challengeId,
    rpId,
    requestJson: JSON.stringify(options)
  };
}

async function finishPasskeyAuthenticationHandler(request, deps) {
  const { auth, db, FieldValue, Timestamp, HttpsError } = deps;
  const data = (request && request.data) || {};
  const challengeId = typeof data.challengeId === "string" ? data.challengeId.trim() : "";
  const authenticationResponseJson = data.authenticationResponseJson;

  if (!isValidFirestoreId(challengeId) || !authenticationResponseJson) {
    throw new HttpsError("invalid-argument", "challengeId and authenticationResponseJson are required.");
  }

  const payload = typeof authenticationResponseJson === "string"
    ? JSON.parse(authenticationResponseJson)
    : authenticationResponseJson;
  const credId = String((payload && (payload.id || payload.rawId)) || "").trim();
  if (!isValidFirestoreId(credId)) {
    throw new HttpsError("invalid-argument", "Credential ID missing in Passkey assertion.");
  }

  const chRef = db.collection("passkeyChallenges").doc(challengeId);
  const chSnap = await chRef.get();
  if (!chSnap.exists) {
    throw new HttpsError("not-found", "Passkey authentication challenge not found.");
  }
  const ch = chSnap.data() || {};
  const currentMillis = nowEpoch(Timestamp);
  if (ch.used || ch.type !== "authentication") {
    throw new HttpsError("permission-denied", "Passkey challenge invalid or already used.");
  }
  if (Number(ch.expiresAtMillis || 0) < currentMillis) {
    throw new HttpsError("deadline-exceeded", "Passkey authentication challenge expired.");
  }

  const credRef = db.collection("passkeyCredentials").doc(credId);
  const credSnap = await credRef.get();
  if (!credSnap.exists) {
    throw new HttpsError("permission-denied", "No registered Passkey found for this credential.");
  }
  const cred = credSnap.data() || {};

  if (ch.expectedRole && cred.role && String(cred.role).toLowerCase() !== ch.expectedRole) {
    throw new HttpsError("permission-denied", "This Passkey belongs to a different account role.");
  }
  if (ch.email && cred.email && String(cred.email).toLowerCase() !== ch.email) {
    throw new HttpsError("permission-denied", "Passkey does not match the entered email.");
  }

  // Enforce Technician loginEnabled & Firebase Auth disabled status on backend before minting token
  if (auth && typeof auth.getUser === "function") {
    const userRec = await auth.getUser(cred.uid);
    if (userRec.disabled) {
      throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
    }
  }
  if (cred.workspaceId) {
    const memberSnap = await db.collection("workspaces").doc(cred.workspaceId).collection("members").doc(cred.uid).get();
    if (memberSnap.exists && memberSnap.data().loginEnabled === false) {
      throw new HttpsError("permission-denied", TECH_LOGIN_DISABLED_MESSAGE);
    }
  }

  let verified;
  try {
    verified = verifyAuthenticationResponse({
      responseJson: payload,
      expectedChallenge: ch.challenge,
      rpId: ch.rpId,
      publicKeyPem: cred.publicKeyPem,
      previousSignCount: Number(cred.signCount || 0),
      allowedApkOrigins: (process.env.WEBAUTHN_ALLOWED_ORIGINS || "").split(",").map((s) => s.trim()).filter(Boolean)
    });
  } catch (err) {
    throw new HttpsError("permission-denied", `Passkey verification failed: ${err.message}`);
  }

  const batch = db.batch();
  batch.update(chRef, { used: true, usedAtMillis: currentMillis });
  batch.update(credRef, {
    signCount: verified.nextSignCount,
    lastUsedAt: FieldValue ? FieldValue.serverTimestamp() : currentMillis
  });
  await batch.commit();

  const customToken = typeof auth.createCustomToken === "function"
    ? await auth.createCustomToken(cred.uid, {
      role: cred.role || "admin",
      workspaceId: cred.workspaceId || "",
      companyId: cred.workspaceId || "",
      passkeyVerified: true
    })
    : `mock_token_${cred.uid}`;

  return {
    verified: true,
    customToken,
    uid: cred.uid,
    email: cred.email || "",
    role: cred.role || "admin",
    workspaceId: cred.workspaceId || "",
    companyId: cred.workspaceId || ""
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
  OFFICIAL_APP_NAME,
  OFFICIAL_COMPANY_NAME,
  TECH_LOGIN_DISABLED_MESSAGE,
  TRASH_RETENTION_MS,
  OTP_TTL_MS,
  OTP_RESEND_COOLDOWN_MS,
  OTP_BLOCK_DURATION_MS,
  OTP_MAX_REQUESTS_PER_WINDOW,
  OTP_MAX_VERIFY_ATTEMPTS,
  isValidEmail,
  validateStrongPassword,
  deriveSafeUsername,
  generateActivationPasskey,
  normalizePasskey,
  hashActivationPasskey,
  hashOtpCode,
  hashScryptSecret,
  verifyScryptSecret,
  requestEmailOtpHandler,
  verifyEmailOtpHandler,
  registerCompanyAdminHandler,
  verifyAdminSecondFactorHandler,
  resetPasswordWithOtpHandler,
  createTechnicianAccountHandler,
  verifyTechnicianActivationHandler,
  completeTechnicianFirstLoginHandler,
  regenerateTechnicianPasskeyHandler,
  completeTechnicianPasswordChangeHandler,
  setTechnicianLoginStatusHandler,
  verifyMemberLoginAccessHandler,
  beginPasskeyRegistrationHandler,
  finishPasskeyRegistrationHandler,
  beginPasskeyAuthenticationHandler,
  finishPasskeyAuthenticationHandler,
  purgeWorkspaceExpiredTrash,
  purgeExpiredTrashCallableHandler,
  scheduledPurgeExpiredTrashHandler
};
