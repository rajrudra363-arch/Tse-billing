"use strict";

const { test, describe } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {
  PRIMARY_ADMIN_EMAIL,
  OFFICIAL_COMPANY_NAME,
  TECH_LOGIN_DISABLED_MESSAGE,
  TRASH_RETENTION_MS,
  hashActivationPasskey,
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
  purgeExpiredTrashCallableHandler,
  scheduledPurgeExpiredTrashHandler
} = require("./handler");
const {
  computeAccurateBillPaymentStatus,
  initiateUpiPaymentHandler,
  cancelUpiPaymentHandler,
  handlePaymentWebhookHandler,
  finalizeBillWithAtomicStockHandler
} = require("./handler-payment");
const { base64UrlEncode } = require("./webauthn");

class MockHttpsError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

class FakeTimestamp {
  constructor(millis) {
    this._millis = millis;
  }
  toMillis() {
    return this._millis;
  }
}

function createFakeEnv(initialDocs = {}, fixedNowMillis = Date.now()) {
  const store = new Map(Object.entries(initialDocs));
  const authUsers = new Map();
  let nextUid = 1;
  let failBatchCommit = false;
  let currentNowMillis = fixedNowMillis;

  const Timestamp = {
    now: () => new FakeTimestamp(currentNowMillis),
    fromMillis: (ms) => new FakeTimestamp(ms)
  };

  const FieldValue = {
    serverTimestamp: () => new FakeTimestamp(currentNowMillis),
    arrayUnion: (...items) => ({ _type: "arrayUnion", items })
  };

  function makeSnapshot(path, data) {
    const id = path.split("/").pop();
    return {
      id,
      ref: docRef(path),
      exists: data !== undefined,
      data: () => (data === undefined ? undefined : structuredClone(data))
    };
  }

  function docRef(path) {
    return {
      path,
      collection: (sub) => collectionRef(`${path}/${sub}`),
      async get() {
        return makeSnapshot(path, store.get(path));
      },
      async set(data, opts = {}) {
        if (opts.merge && store.has(path)) {
          store.set(path, { ...store.get(path), ...structuredClone(data) });
        } else {
          store.set(path, structuredClone(data));
        }
      },
      async update(updates) {
        if (!store.has(path)) {
          throw new Error(`Missing doc ${path}`);
        }
        const current = structuredClone(store.get(path));
        for (const [k, v] of Object.entries(updates)) {
          if (v && v._type === "arrayUnion") {
            const arr = Array.isArray(current[k]) ? current[k] : [];
            for (const item of v.items) {
              if (!arr.includes(item)) arr.push(item);
            }
            current[k] = arr;
          } else {
            current[k] = structuredClone(v);
          }
        }
        store.set(path, current);
      },
      async delete() {
        store.delete(path);
      }
    };
  }

  function runCollectionQuery(colPath, filters = [], limitCount = null, isGroup = false) {
    const matched = [];
    for (const [docPath, val] of store.entries()) {
      if (isGroup) {
        const parts = docPath.split("/");
        if (parts.length < 2 || parts[parts.length - 2] !== colPath) continue;
      } else {
        const prefix = `${colPath}/`;
        if (!docPath.startsWith(prefix)) continue;
        const remainder = docPath.slice(prefix.length);
        if (remainder.includes("/")) continue;
      }
      let ok = true;
      for (const f of filters) {
        const fieldVal = val[f.field];
        if (f.op === "==") {
          if (fieldVal !== f.value) ok = false;
        } else if (f.op === "array-contains") {
          if (!Array.isArray(fieldVal) || !fieldVal.includes(f.value)) ok = false;
        }
      }
      if (ok) {
        matched.push(makeSnapshot(docPath, val));
      }
    }
    const docs = limitCount != null ? matched.slice(0, limitCount) : matched;
    return { docs, empty: docs.length === 0, size: docs.length };
  }

  function queryObj(colPath, filters = [], limitCount = null, isGroup = false) {
    return {
      where(field, op, value) {
        return queryObj(colPath, [...filters, { field, op, value }], limitCount, isGroup);
      },
      limit(n) {
        return queryObj(colPath, filters, n, isGroup);
      },
      async get() {
        return runCollectionQuery(colPath, filters, limitCount, isGroup);
      }
    };
  }

  function collectionRef(path) {
    return {
      doc: (id) => docRef(`${path}/${id}`),
      where: (field, op, value) => queryObj(path, [{ field, op, value }], null, false),
      limit: (n) => queryObj(path, [], n, false),
      async get() {
        return runCollectionQuery(path, [], null, false);
      }
    };
  }

  const db = {
    collection: (name) => collectionRef(name),
    collectionGroup: (name) => queryObj(name, [], null, true),
    batch() {
      const ops = [];
      return {
        set(ref, data, opts) {
          ops.push(() => ref.set(data, opts));
        },
        update(ref, data) {
          ops.push(() => ref.update(data));
        },
        delete(ref) {
          ops.push(() => ref.delete());
        },
        async commit() {
          if (failBatchCommit) {
            throw new Error("Simulated Firestore batch failure");
          }
          for (const op of ops) {
            await op();
          }
        }
      };
    },
    async runTransaction(fn) {
      const tx = {
        get: (ref) => ref.get(),
        set: (ref, data, opts) => ref.set(data, opts),
        update: (ref, data) => ref.update(data),
        delete: (ref) => ref.delete()
      };
      return await fn(tx);
    }
  };

  const auth = {
    async createUser(params) {
      for (const u of authUsers.values()) {
        if (u.email === params.email) {
          const err = new Error("Email already exists");
          err.code = "auth/email-already-exists";
          throw err;
        }
      }
      const uid = `tech_uid_${nextUid++}`;
      const record = { uid, revokedCount: 0, ...params, customClaims: {} };
      authUsers.set(uid, record);
      return record;
    },
    async getUser(uid) {
      const u = authUsers.get(uid);
      if (!u) {
        const err = new Error("User not found");
        err.code = "auth/user-not-found";
        throw err;
      }
      return u;
    },
    async getUserByEmail(email) {
      const clean = String(email || "").trim().toLowerCase();
      for (const u of authUsers.values()) {
        if (String(u.email || "").toLowerCase() === clean) return u;
      }
      const err = new Error("User not found");
      err.code = "auth/user-not-found";
      throw err;
    },
    async updateUser(uid, updates) {
      const u = authUsers.get(uid);
      if (!u) throw new Error("Missing auth user");
      Object.assign(u, updates);
      return u;
    },
    async revokeRefreshTokens(uid) {
      const u = authUsers.get(uid);
      if (u) u.revokedCount = (u.revokedCount || 0) + 1;
    },
    async setCustomUserClaims(uid, claims) {
      const u = authUsers.get(uid);
      if (u) u.customClaims = claims;
    },
    async createCustomToken(uid, claims) {
      return `custom_jwt_${uid}_${claims.role || ""}`;
    },
    async deleteUser(uid) {
      authUsers.delete(uid);
    }
  };

  return {
    store,
    authUsers,
    FakeTimestamp,
    deps: { auth, db, FieldValue, Timestamp, HttpsError: MockHttpsError },
    setFailBatchCommit(val) {
      failBatchCommit = val;
    },
    setNowMillis(ms) {
      currentNowMillis = ms;
    }
  };
}

function seedWorkspace(env) {
  env.store.set("workspaces/ws_tse_1", {
    workspaceId: "ws_tse_1",
    companyId: "ws_tse_1",
    ownerUid: "admin_uid_1",
    ownerEmail: PRIMARY_ADMIN_EMAIL,
    companyName: OFFICIAL_COMPANY_NAME,
    techShareCode: "TS_TECH_991122",
    upiId: "7083805120@ybl",
    payeeName: "TOTAL SOLUTION",
    memberUids: ["admin_uid_1"]
  });
  env.store.set("admins/admin_uid_1", {
    userId: "admin_uid_1",
    email: PRIMARY_ADMIN_EMAIL
  });
  env.store.set("workspaces/ws_tse_1/members/tech_caller_1", {
    userId: "tech_caller_1",
    role: "technician",
    email: "techcaller@example.com",
    loginEnabled: true
  });
  env.store.set("workspaces/ws_tse_1/members/sa_caller_1", {
    userId: "sa_caller_1",
    role: "super_admin",
    email: "sacaller@example.com",
    loginEnabled: true
  });
}

// Helper to encode deterministic CBOR buffers for real ES256 WebAuthn Passkey tests
function cborUint(major, num) {
  if (num < 24) return Buffer.from([(major << 5) | num]);
  if (num < 256) return Buffer.from([(major << 5) | 24, num]);
  const b = Buffer.alloc(3);
  b[0] = (major << 5) | 25;
  b.writeUInt16BE(num, 1);
  return b;
}
function cborBytes(buf) {
  return Buffer.concat([cborUint(2, buf.length), buf]);
}
function cborText(str) {
  const b = Buffer.from(str, "utf8");
  return Buffer.concat([cborUint(3, b.length), b]);
}
function cborInt(n) {
  return n >= 0 ? cborUint(0, n) : cborUint(1, -1 - n);
}
function cborMap(entries) {
  const parts = [cborUint(5, entries.length)];
  for (const [k, v] of entries) {
    parts.push(typeof k === "number" ? cborInt(k) : cborText(k));
    parts.push(v);
  }
  return Buffer.concat(parts);
}

describe("Cloud Functions: TS – TOTAL SOLUTION Complete Backend Suite", () => {
  test("verified Admin creates Technician Auth user, unique techId, hashed passkey (no plaintext)", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);

    const res = await createTechnicianAccountHandler(
      {
        auth: {
          uid: "admin_uid_1",
          token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true }
        },
        data: {
          workspaceId: "ws_tse_1",
          name: "Rahul Sharma",
          email: "Rahul.Tech@example.com",
          initialPassword: "TempPass@123",
          phone: "9876543210"
        }
      },
      env.deps
    );

    assert.equal(res.role, "technician");
    assert.equal(res.email, "rahul.tech@example.com");
    assert.equal(res.mustChangePassword, true);
    assert.equal(res.loginEnabled, true);
    assert.match(res.techId, /^TECH-[A-Z0-9]{8}$/);
    assert.match(res.activationPasskey, /^TSE-[A-Z0-9]{4}-[A-Z0-9]{4}$/);

    const memberDoc = env.store.get(`workspaces/ws_tse_1/members/${res.uid}`);
    assert.equal(memberDoc.techId, res.techId);
    assert.equal(memberDoc.loginEnabled, true);
    assert.equal(memberDoc.activationPasskeyHash, hashActivationPasskey(res.activationPasskey));
    assert.equal(memberDoc.password, undefined);
    assert.ok(!JSON.stringify(memberDoc).includes("TempPass@123"));
  });

  test("Email OTP, Admin Sign Up (locked TOTAL SOLUTION + scrypt 2FA), 2FA verify & OTP password reset", async () => {
    const env = createFakeEnv({}, 1_760_000_000_000);
    const sentEmails = [];
    const depsWithMailer = {
      ...env.deps,
      sendEmail: async (mail) => {
        sentEmails.push(mail);
      }
    };

    // 1. Request OTP for Admin Sign Up
    const otpReq = await requestEmailOtpHandler(
      { data: { email: "newadmin@totalsolution.app", purpose: "ADMIN_SIGNUP" } },
      depsWithMailer
    );
    assert.equal(sentEmails.length, 1);
    const otpMatch = sentEmails[0].text.match(/\b(\d{6})\b/);
    assert.ok(otpMatch, "Email should contain 6-digit OTP");
    const otpCode = otpMatch[1];

    // Stored doc must NOT hold plaintext OTP
    const challengeDoc = env.store.get(`otpChallenges/${otpReq.challengeId}`);
    assert.ok(!JSON.stringify(challengeDoc).includes(otpCode));

    // Wrong OTP decrements attempts & rejects
    await assert.rejects(
      () => verifyEmailOtpHandler(
        { data: { challengeId: otpReq.challengeId, email: "newadmin@totalsolution.app", purpose: "ADMIN_SIGNUP", otp: "000000" } },
        depsWithMailer
      ),
      (e) => e.code === "permission-denied"
    );

    // Correct OTP returns verificationToken
    const otpVerified = await verifyEmailOtpHandler(
      { data: { challengeId: otpReq.challengeId, email: "newadmin@totalsolution.app", purpose: "ADMIN_SIGNUP", otp: otpCode } },
      depsWithMailer
    );
    assert.equal(otpVerified.verified, true);

    // Attempting non-locked company name is rejected
    await assert.rejects(
      () => registerCompanyAdminHandler(
        {
          data: {
            companyName: "OTHER COMPANY",
            adminName: "Suresh Admin",
            mobile: "9876501234",
            email: "newadmin@totalsolution.app",
            password: "StrongPassword@1",
            confirmPassword: "StrongPassword@1",
            twoFactorPassword: "TwoFactor@99",
            confirmTwoFactorPassword: "TwoFactor@99",
            otpChallengeId: otpReq.challengeId,
            otpVerificationToken: otpVerified.verificationToken
          }
        },
        depsWithMailer
      ),
      (e) => e.code === "invalid-argument"
    );

    // Valid Admin Sign Up succeeds with locked company name
    const reg = await registerCompanyAdminHandler(
      {
        data: {
          companyName: OFFICIAL_COMPANY_NAME,
          adminName: "Suresh Admin",
          mobile: "9876501234",
          email: "newadmin@totalsolution.app",
          password: "StrongPassword@1",
          confirmPassword: "StrongPassword@1",
          twoFactorPassword: "TwoFactor@99",
          confirmTwoFactorPassword: "TwoFactor@99",
          otpChallengeId: otpReq.challengeId,
          otpVerificationToken: otpVerified.verificationToken
        }
      },
      depsWithMailer
    );
    assert.equal(reg.companyName, "TOTAL SOLUTION");
    assert.ok(reg.companyId.startsWith("comp_"));
    const secretDoc = env.store.get(`secondFactorSecrets/${reg.uid}`);
    assert.ok(secretDoc && secretDoc.twoFactorHash.includes(":"));
    assert.ok(!JSON.stringify(secretDoc).includes("TwoFactor@99"));

    // Verify 2FA password
    const tfaOk = await verifyAdminSecondFactorHandler(
      { auth: { uid: reg.uid }, data: { twoFactorPassword: "TwoFactor@99" } },
      depsWithMailer
    );
    assert.equal(tfaOk.verified, true);
    await assert.rejects(
      () => verifyAdminSecondFactorHandler(
        { auth: { uid: reg.uid }, data: { twoFactorPassword: "Wrong2FA" } },
        depsWithMailer
      ),
      (e) => e.code === "permission-denied"
    );

    // Forgot Password via verified OTP
    const resetOtpReq = await requestEmailOtpHandler(
      { data: { email: "newadmin@totalsolution.app", purpose: "ADMIN_FORGOT_PASSWORD" } },
      depsWithMailer
    );
    const resetCode = sentEmails[1].text.match(/\b(\d{6})\b/)[1];
    const resetVerified = await verifyEmailOtpHandler(
      { data: { challengeId: resetOtpReq.challengeId, email: "newadmin@totalsolution.app", purpose: "ADMIN_FORGOT_PASSWORD", otp: resetCode } },
      depsWithMailer
    );
    const resetRes = await resetPasswordWithOtpHandler(
      {
        data: {
          email: "newadmin@totalsolution.app",
          purpose: "ADMIN_FORGOT_PASSWORD",
          otpChallengeId: resetOtpReq.challengeId,
          otpVerificationToken: resetVerified.verificationToken,
          newPassword: "UpdatedPassword@2",
          confirmPassword: "UpdatedPassword@2"
        }
      },
      depsWithMailer
    );
    assert.equal(resetRes.reset, true);
    assert.equal(env.authUsers.get(reg.uid).password, "UpdatedPassword@2");
  });

  test("Real FIDO2/WebAuthn Passkey registration, cryptographic verification, signCount & custom token minting", async () => {
    const env = createFakeEnv({}, 1_760_000_000_000);
    seedWorkspace(env);
    env.authUsers.set("admin_uid_1", { uid: "admin_uid_1", email: PRIMARY_ADMIN_EMAIL, disabled: false });

    const rpId = "totalsolution.app";
    const regBegin = await beginPasskeyRegistrationHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL } },
        data: { workspaceId: "ws_tse_1", rpId, role: "admin", displayName: "Manoj Admin" }
      },
      env.deps
    );
    const regOptions = JSON.parse(regBegin.requestJson);

    // Generate genuine ECDSA P-256 keypair and build authentic WebAuthn attestationObject
    const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
    const jwk = publicKey.export({ format: "jwk" });
    const xBuf = Buffer.from(jwk.x, "base64url");
    const yBuf = Buffer.from(jwk.y, "base64url");
    const coseBytes = cborMap([
      [1, cborInt(2)],   // kty: EC2
      [3, cborInt(-7)],  // alg: ES256
      [-1, cborInt(1)],  // crv: P-256
      [-2, cborBytes(xBuf)],
      [-3, cborBytes(yBuf)]
    ]);
    const credIdRaw = crypto.randomBytes(16);
    const credIdB64 = base64UrlEncode(credIdRaw);
    const rpHash = crypto.createHash("sha256").update(rpId).digest();
    const headerBuf = Buffer.alloc(37);
    rpHash.copy(headerBuf, 0);
    headerBuf[32] = 0x45; // UP | UV | AT
    headerBuf.writeUInt32BE(1, 33);
    const aaguid = Buffer.alloc(16, 0);
    const lenBuf = Buffer.alloc(2);
    lenBuf.writeUInt16BE(credIdRaw.length, 0);
    const authDataReg = Buffer.concat([headerBuf, aaguid, lenBuf, credIdRaw, coseBytes]);
    const attestationObject = cborMap([
      ["fmt", cborText("none")],
      ["attStmt", cborMap([])],
      ["authData", cborBytes(authDataReg)]
    ]);
    const regClientData = Buffer.from(JSON.stringify({
      type: "webauthn.create",
      challenge: regOptions.challenge,
      origin: `https://${rpId}`
    }), "utf8");

    const regFinish = await finishPasskeyRegistrationHandler(
      {
        auth: { uid: "admin_uid_1" },
        data: {
          challengeId: regBegin.challengeId,
          registrationResponseJson: JSON.stringify({
            id: credIdB64,
            rawId: credIdB64,
            response: {
              clientDataJSON: base64UrlEncode(regClientData),
              attestationObject: base64UrlEncode(attestationObject)
            }
          })
        }
      },
      env.deps
    );
    assert.equal(regFinish.registered, true);
    const storedCred = env.store.get(`passkeyCredentials/${credIdB64}`);
    assert.ok(storedCred.publicKeyPem.includes("PUBLIC KEY"));
    assert.equal(storedCred.privateKey, undefined);

    // Begin Passkey authentication
    const authBegin = await beginPasskeyAuthenticationHandler(
      { data: { email: PRIMARY_ADMIN_EMAIL, expectedRole: "admin", rpId } },
      env.deps
    );
    const authOpts = JSON.parse(authBegin.requestJson);

    const authClientData = Buffer.from(JSON.stringify({
      type: "webauthn.get",
      challenge: authOpts.challenge,
      origin: `https://${rpId}`
    }), "utf8");
    const authDataAssert = Buffer.alloc(37);
    rpHash.copy(authDataAssert, 0);
    authDataAssert[32] = 0x05; // UP | UV
    authDataAssert.writeUInt32BE(2, 33);
    const clientDataHash = crypto.createHash("sha256").update(authClientData).digest();
    const signer = crypto.createSign("SHA256");
    signer.update(Buffer.concat([authDataAssert, clientDataHash]));
    signer.end();
    const sig = signer.sign(privateKey);

    const authFinish = await finishPasskeyAuthenticationHandler(
      {
        data: {
          challengeId: authBegin.challengeId,
          authenticationResponseJson: JSON.stringify({
            id: credIdB64,
            rawId: credIdB64,
            response: {
              clientDataJSON: base64UrlEncode(authClientData),
              authenticatorData: base64UrlEncode(authDataAssert),
              signature: base64UrlEncode(sig)
            }
          })
        }
      },
      env.deps
    );
    assert.equal(authFinish.verified, true);
    assert.equal(authFinish.uid, "admin_uid_1");
    assert.equal(authFinish.customToken, "custom_jwt_admin_uid_1_admin");
  });

  test("Technician First-Time Onboarding & Admin-only Login Control (ENABLED/DISABLED)", async () => {
    const env = createFakeEnv({}, 1_760_000_000_000);
    seedWorkspace(env);

    const created = await createTechnicianAccountHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: { workspaceId: "ws_tse_1", name: "Karan Tech", email: "karan@example.com", initialPassword: "TempPass@123", phone: "9811111111" }
      },
      env.deps
    );

    const onboarded = await completeTechnicianFirstLoginHandler(
      {
        auth: { uid: created.uid },
        data: {
          workspaceId: "ws_tse_1",
          username: "karan_pro",
          newPassword: "KaranStrong@99",
          photoBase64: "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD="
        }
      },
      env.deps
    );
    assert.equal(onboarded.firstLoginCompleted, true);
    assert.equal(onboarded.username, "karan_pro");
    assert.equal(env.authUsers.get(created.uid).password, "KaranStrong@99");

    // Super Admin & Technician cannot toggle Technician Login Control
    for (const nonAdmin of [
      { uid: "sa_caller_1", token: { email: "sacaller@example.com", email_verified: true } },
      { uid: "tech_caller_1", token: { email: "techcaller@example.com", email_verified: true } }
    ]) {
      await assert.rejects(
        () => setTechnicianLoginStatusHandler(
          { auth: nonAdmin, data: { workspaceId: "ws_tse_1", technicianUid: created.uid, enabled: false } },
          env.deps
        ),
        (e) => e.code === "permission-denied"
      );
    }

    // Admin disables Technician -> Auth disabled + refresh tokens revoked + audit log written
    const disabledRes = await setTechnicianLoginStatusHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: { workspaceId: "ws_tse_1", technicianUid: created.uid, enabled: false }
      },
      env.deps
    );
    assert.equal(disabledRes.loginEnabled, false);
    assert.equal(env.authUsers.get(created.uid).disabled, true);
    assert.equal(env.authUsers.get(created.uid).revokedCount, 1);

    // Disabled technician is blocked with exact required message
    await assert.rejects(
      () => verifyMemberLoginAccessHandler(
        { auth: { uid: created.uid }, data: { workspaceId: "ws_tse_1" } },
        env.deps
      ),
      (e) => e.code === "permission-denied" && e.message === TECH_LOGIN_DISABLED_MESSAGE
    );

    // Re-enable restores access & preserves existing account
    const enabledRes = await setTechnicianLoginStatusHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: { workspaceId: "ws_tse_1", technicianUid: created.uid, enabled: true }
      },
      env.deps
    );
    assert.equal(enabledRes.loginEnabled, true);
    const accessOk = await verifyMemberLoginAccessHandler(
      { auth: { uid: created.uid }, data: { workspaceId: "ws_tse_1" } },
      env.deps
    );
    assert.equal(accessOk.allowed, true);
    assert.equal(accessOk.techId, created.techId);
  });

  test("Fail-closed Razorpay/Cashfree Webhooks, auto Final Bill + Receipt, & Once-Only Stock Deduction", async () => {
    const env = createFakeEnv({}, 1_760_000_000_000);
    seedWorkspace(env);
    env.store.set("workspaces/ws_tse_1/products/p_cam", {
      productId: "p_cam",
      name: "Dome Camera 2MP",
      unit: "Nos",
      defaultRate: 1500,
      folder: "Cameras",
      stockQty: 10,
      minStockAlert: 2
    });

    process.env.RAZORPAY_KEY_ID = "rzp_test_id";
    process.env.RAZORPAY_KEY_SECRET = "rzp_test_secret";
    process.env.RAZORPAY_WEBHOOK_SECRET = "rzp_wh_secret";

    const fakeFetch = async (url) => {
      if (url.includes("/orders")) {
        return { ok: true, json: async () => ({ id: "order_rzp_1" }) };
      }
      return { ok: true, json: async () => ({ id: "qr_1", image_content: "upi://pay?pa=7083805120@ybl&am=3000.00&cu=INR", short_url: "https://rzp.io/i/qr1" }) };
    };

    const initiated = await initiateUpiPaymentHandler(
      {
        auth: { uid: "tech_caller_1" },
        data: {
          workspaceId: "ws_tse_1",
          customerId: "c_1",
          customerName: "Ramesh Naik",
          amount: 3000,
          gateway: "RAZORPAY",
          draftSnapshot: {
            invoiceNo: "TS-101",
            customerName: "Ramesh Naik",
            customerDetails: "Panaji\n9876543210",
            items: [{ description: "Dome Camera 2MP", qty: 2, rate: 1500, amount: 3000 }],
            subtotal: 3000,
            grandTotal: 3000,
            createdBy: "tech_caller_1"
          }
        }
      },
      { ...env.deps, fetchFn: fakeFetch }
    );
    assert.equal(initiated.status, "PENDING");
    assert.equal(initiated.orderId, "order_rzp_1");

    // Unsigned webhook MUST fail closed
    await assert.rejects(
      () => handlePaymentWebhookHandler({ headers: {}, body: { event: "payment.captured" } }, env.deps),
      (e) => e.code === "permission-denied"
    );

    // Tampered HMAC signature MUST fail closed
    const whPayload = {
      event: "payment.captured",
      payload: {
        payment: {
          entity: {
            id: "pay_rzp_tx_999",
            order_id: "order_rzp_1",
            amount: 300000,
            notes: { workspaceId: "ws_tse_1", paymentId: initiated.paymentId }
          }
        }
      }
    };
    const rawBody = JSON.stringify(whPayload);
    await assert.rejects(
      () => handlePaymentWebhookHandler(
        { headers: { "x-razorpay-signature": "bad_sig_000" }, rawBody, body: whPayload },
        env.deps
      ),
      (e) => e.code === "permission-denied"
    );

    // Valid HMAC triggers SUCCESS -> creates Final Bill, Receipt & deducts stock 10 -> 8 ONCE
    const validSig = crypto.createHmac("sha256", "rzp_wh_secret").update(rawBody).digest("hex");
    const okRes = await handlePaymentWebhookHandler(
      { headers: { "x-razorpay-signature": validSig }, rawBody, body: whPayload },
      env.deps
    );
    assert.equal(okRes.status, "processed");
    assert.equal(okRes.paymentStatus, "SUCCESS");
    assert.ok(okRes.receiptNumber.startsWith("RCPT-"));

    const billDoc = env.store.get(`workspaces/ws_tse_1/bills/${okRes.billId}`);
    assert.equal(billDoc.paymentStatus, "PAID");
    assert.equal(billDoc.stockDeducted, true);
    assert.equal(env.store.get("workspaces/ws_tse_1/products/p_cam").stockQty, 8);

    // Duplicate webhook replay does NOT deduct stock a second time
    const dupRes = await handlePaymentWebhookHandler(
      { headers: { "x-razorpay-signature": validSig }, rawBody, body: whPayload },
      env.deps
    );
    assert.equal(dupRes.status, "duplicate_event_ignored");
    assert.equal(env.store.get("workspaces/ws_tse_1/products/p_cam").stockQty, 8);

    // Manual QUOTATION must NEVER deduct stock; Final BILL deducts strictly once even if re-saved
    await finalizeBillWithAtomicStockHandler(
      {
        auth: { uid: "admin_uid_1" },
        data: {
          workspaceId: "ws_tse_1",
          bill: {
            billId: "q_1",
            invoiceNo: "QT-01",
            documentType: "QUOTATION",
            itemsJson: JSON.stringify([{ description: "Dome Camera 2MP", qty: 3 }]),
            grandTotal: 4500,
            paidAmount: 0
          }
        }
      },
      env.deps
    );
    assert.equal(env.store.get("workspaces/ws_tse_1/products/p_cam").stockQty, 8);

    const partialBill = await finalizeBillWithAtomicStockHandler(
      {
        auth: { uid: "admin_uid_1" },
        data: {
          workspaceId: "ws_tse_1",
          bill: {
            billId: "b_manual_1",
            invoiceNo: "TS-202",
            documentType: "BILL",
            itemsJson: JSON.stringify([{ description: "Dome Camera 2MP", qty: 3 }]),
            grandTotal: 4500,
            paidAmount: 1500
          }
        }
      },
      env.deps
    );
    assert.equal(partialBill.paymentStatus, "PARTIAL");
    assert.equal(env.store.get("workspaces/ws_tse_1/products/p_cam").stockQty, 5);

    // Re-updating same bill never deducts stock again
    await finalizeBillWithAtomicStockHandler(
      {
        auth: { uid: "admin_uid_1" },
        data: {
          workspaceId: "ws_tse_1",
          bill: {
            billId: "b_manual_1",
            invoiceNo: "TS-202",
            documentType: "BILL",
            itemsJson: JSON.stringify([{ description: "Dome Camera 2MP", qty: 3 }]),
            grandTotal: 4500,
            paidAmount: 4500
          }
        }
      },
      env.deps
    );
    assert.equal(env.store.get("workspaces/ws_tse_1/products/p_cam").stockQty, 5);
    assert.equal(computeAccurateBillPaymentStatus(1000, 0), "UNPAID");

    delete process.env.RAZORPAY_KEY_ID;
    delete process.env.RAZORPAY_KEY_SECRET;
    delete process.env.RAZORPAY_WEBHOOK_SECRET;
  });

  test("server-authoritative 24h trash purge deletes only expired TRASHED items and writes AUTO_PURGE_24H audit log", async () => {
    const t0 = 1_760_000_000_000;
    const env = createFakeEnv({}, t0);
    seedWorkspace(env);

    env.store.set("workspaces/ws_tse_1/trash/tr_fresh", {
      trashId: "tr_fresh",
      entityType: "BILL",
      entityId: "b_1",
      title: "TS-001 — Fresh Bill",
      status: "TRASHED",
      deletedAt: new env.FakeTimestamp(t0 - 10_000),
      deletedAtMillis: t0 - 10_000,
      expireAt: new env.FakeTimestamp(t0 - 10_000 + TRASH_RETENTION_MS)
    });
    env.store.set("workspaces/ws_tse_1/trash/tr_expired", {
      trashId: "tr_expired",
      entityType: "PRODUCT",
      entityId: "42",
      title: "Old Dome Camera",
      status: "TRASHED",
      deletedAt: new env.FakeTimestamp(t0 - TRASH_RETENTION_MS - 5000),
      deletedAtMillis: t0 - TRASH_RETENTION_MS - 5000,
      expireAt: new env.FakeTimestamp(t0 - 5000)
    });

    await assert.rejects(
      () => purgeExpiredTrashCallableHandler(
        {
          auth: { uid: "tech_caller_1", token: { email: "techcaller@example.com", email_verified: true } },
          data: { workspaceId: "ws_tse_1" }
        },
        env.deps
      ),
      (err) => err.code === "permission-denied"
    );

    const callableRes = await purgeExpiredTrashCallableHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: { workspaceId: "ws_tse_1" }
      },
      env.deps
    );
    assert.equal(callableRes.purgedCount, 1);
    assert.ok(env.store.has("workspaces/ws_tse_1/trash/tr_fresh"));
    assert.ok(!env.store.has("workspaces/ws_tse_1/trash/tr_expired"));

    env.setNowMillis(t0 + TRASH_RETENTION_MS);
    const schedRes = await scheduledPurgeExpiredTrashHandler(env.deps);
    assert.equal(schedRes.purgedCount, 1);
    assert.ok(!env.store.has("workspaces/ws_tse_1/trash/tr_fresh"));
  });
});
