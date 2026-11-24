"use strict";

const { test, describe } = require("node:test");
const assert = require("node:assert/strict");
const {
  PRIMARY_ADMIN_EMAIL,
  TRASH_RETENTION_MS,
  hashActivationPasskey,
  createTechnicianAccountHandler,
  verifyTechnicianActivationHandler,
  regenerateTechnicianPasskeyHandler,
  completeTechnicianPasswordChangeHandler,
  purgeExpiredTrashCallableHandler,
  scheduledPurgeExpiredTrashHandler
} = require("./handler");

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

  function runCollectionQuery(colPath, filters = [], limitCount = null) {
    const prefix = `${colPath}/`;
    const matched = [];
    for (const [docPath, val] of store.entries()) {
      if (!docPath.startsWith(prefix)) continue;
      const remainder = docPath.slice(prefix.length);
      if (remainder.includes("/")) continue; // direct children only
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

  function queryObj(colPath, filters = [], limitCount = null) {
    return {
      where(field, op, value) {
        return queryObj(colPath, [...filters, { field, op, value }], limitCount);
      },
      limit(n) {
        return queryObj(colPath, filters, n);
      },
      async get() {
        return runCollectionQuery(colPath, filters, limitCount);
      }
    };
  }

  function collectionRef(path) {
    return {
      doc: (id) => docRef(`${path}/${id}`),
      where: (field, op, value) => queryObj(path, [{ field, op, value }], null),
      limit: (n) => queryObj(path, [], n),
      async get() {
        return runCollectionQuery(path, [], null);
      }
    };
  }

  const db = {
    collection: (name) => collectionRef(name),
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
      const record = { uid, ...params, customClaims: {} };
      authUsers.set(uid, record);
      return record;
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
    async setCustomUserClaims(uid, claims) {
      const u = authUsers.get(uid);
      if (u) u.customClaims = claims;
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
    ownerUid: "admin_uid_1",
    ownerEmail: PRIMARY_ADMIN_EMAIL,
    companyName: "TOTAL SOLUTION ENTERPRISES",
    techShareCode: "TSE_TECH_991122",
    memberUids: ["admin_uid_1"]
  });
  env.store.set("admins/admin_uid_1", {
    userId: "admin_uid_1",
    email: PRIMARY_ADMIN_EMAIL
  });
  env.store.set("workspaces/ws_tse_1/members/tech_caller_1", {
    userId: "tech_caller_1",
    role: "technician",
    email: "techcaller@example.com"
  });
  env.store.set("workspaces/ws_tse_1/members/sa_caller_1", {
    userId: "sa_caller_1",
    role: "super_admin",
    email: "sacaller@example.com"
  });
}

describe("Cloud Functions: Technician Auth, Activation Passkey & 24h Server Purge", () => {
  test("verified Admin creates Technician Auth user, hashed passkey (no plaintext in Firestore)", async () => {
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
    assert.equal(res.workspaceId, "ws_tse_1");
    assert.match(res.activationPasskey, /^TSE-[A-Z0-9]{4}-[A-Z0-9]{4}$/);

    const ws = env.store.get("workspaces/ws_tse_1");
    assert.ok(ws.memberUids.includes(res.uid));

    const memberDoc = env.store.get(`workspaces/ws_tse_1/members/${res.uid}`);
    assert.equal(memberDoc.role, "technician");
    assert.equal(memberDoc.mustChangePassword, true);
    assert.equal(memberDoc.activationPasskeyHash, hashActivationPasskey(res.activationPasskey));
    assert.equal(memberDoc.activationPasskey, undefined);
    assert.equal(memberDoc.password, undefined);
    assert.equal(memberDoc.initialPassword, undefined);
    assert.ok(!JSON.stringify(memberDoc).includes("TempPass@123"));
    assert.ok(!JSON.stringify(memberDoc).includes(res.activationPasskey));

    const accountDoc = env.store.get(`workspaces/ws_tse_1/accounts/${res.username}`);
    assert.equal(accountDoc.role, "technician");
    assert.equal(accountDoc.passwordHash, "FIREBASE_EMAIL_AUTH_ONLY");
    assert.ok(!JSON.stringify(accountDoc).includes("TempPass@123"));

    const authUser = env.authUsers.get(res.uid);
    assert.equal(authUser.customClaims.role, "technician");
    assert.equal(authUser.customClaims.workspaceId, "ws_tse_1");
  });

  test("verifyTechnicianActivationHandler validates email + passkey & blocks unauthorized users", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);

    const created = await createTechnicianAccountHandler(
      {
        auth: {
          uid: "admin_uid_1",
          token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true }
        },
        data: {
          workspaceId: "ws_tse_1",
          name: "Amit Verma",
          email: "amit.tech@example.com",
          username: "amit_tech",
          initialPassword: "TempPass@123"
        }
      },
      env.deps
    );

    // Works with lowercase/no-hyphen normalized input too
    const verified = await verifyTechnicianActivationHandler(
      {
        data: {
          email: "AMIT.TECH@example.com",
          activationPasskey: created.activationPasskey.toLowerCase().replace(/-/g, "")
        }
      },
      env.deps
    );
    assert.equal(verified.valid, true);
    assert.equal(verified.username, "amit_tech");
    assert.equal(verified.workspaceId, "ws_tse_1");
    assert.equal(verified.companyName, "TOTAL SOLUTION ENTERPRISES");

    const memberAfter = env.store.get(`workspaces/ws_tse_1/members/${created.uid}`);
    assert.ok(memberAfter.activatedAt);

    // Wrong passkey rejected
    await assert.rejects(
      () => verifyTechnicianActivationHandler(
        { data: { email: "amit.tech@example.com", activationPasskey: "TSE-WRONG-9999" } },
        env.deps
      ),
      (err) => err.code === "permission-denied"
    );

    // Unknown email rejected
    await assert.rejects(
      () => verifyTechnicianActivationHandler(
        { data: { email: "stranger@example.com", activationPasskey: created.activationPasskey } },
        env.deps
      ),
      (err) => err.code === "permission-denied"
    );

    // Primary Admin email cannot bypass passkey gate
    await assert.rejects(
      () => verifyTechnicianActivationHandler(
        { data: { email: PRIMARY_ADMIN_EMAIL, activationPasskey: created.activationPasskey } },
        env.deps
      ),
      (err) => err.code === "permission-denied"
    );
  });

  test("regenerateTechnicianPasskeyHandler issues new passkey and invalidates previous passkey", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);

    const created = await createTechnicianAccountHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: {
          workspaceId: "ws_tse_1",
          name: "Rohan Tech",
          email: "rohan@example.com",
          initialPassword: "TempPass@123"
        }
      },
      env.deps
    );

    const regen = await regenerateTechnicianPasskeyHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: { workspaceId: "ws_tse_1", technicianUid: created.uid }
      },
      env.deps
    );
    assert.notEqual(regen.activationPasskey, created.activationPasskey);

    // Old passkey fails
    await assert.rejects(
      () => verifyTechnicianActivationHandler(
        { data: { email: "rohan@example.com", activationPasskey: created.activationPasskey } },
        env.deps
      ),
      (err) => err.code === "permission-denied"
    );

    // New passkey passes
    const ok = await verifyTechnicianActivationHandler(
      { data: { email: "rohan@example.com", activationPasskey: regen.activationPasskey } },
      env.deps
    );
    assert.equal(ok.valid, true);
  });

  test("rejects unauthenticated, Technician, Super Admin, outsider, and role escalation callers", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);

    await assert.rejects(
      () => createTechnicianAccountHandler({ auth: null, data: { workspaceId: "ws_tse_1", name: "Test User", email: "t@example.com", initialPassword: "TempPass@123" } }, env.deps),
      (err) => err.code === "unauthenticated"
    );

    for (const caller of [
      { uid: "tech_caller_1", token: { email: "techcaller@example.com", email_verified: true } },
      { uid: "sa_caller_1", token: { email: "sacaller@example.com", email_verified: true } },
      { uid: "random_outsider", token: { email: "outsider@example.com", email_verified: true } }
    ]) {
      await assert.rejects(
        () => createTechnicianAccountHandler(
          {
            auth: caller,
            data: {
              workspaceId: "ws_tse_1",
              name: "Another Tech",
              email: "another@example.com",
              initialPassword: "TempPass@123"
            }
          },
          env.deps
        ),
        (err) => err.code === "permission-denied"
      );
    }

    await assert.rejects(
      () => createTechnicianAccountHandler(
        {
          auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
          data: {
            workspaceId: "ws_tse_1",
            name: "Escalated Tech",
            email: "escalated@example.com",
            initialPassword: "TempPass@123",
            role: "admin"
          }
        },
        env.deps
      ),
      (err) => err.code === "permission-denied"
    );
  });

  test("rolls back Firebase Auth user if Firestore batch commit fails", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);
    env.setFailBatchCommit(true);

    await assert.rejects(
      () => createTechnicianAccountHandler(
        {
          auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
          data: {
            workspaceId: "ws_tse_1",
            name: "Rollback Tech",
            email: "rollback@example.com",
            initialPassword: "TempPass@123"
          }
        },
        env.deps
      ),
      (err) => err.code === "internal"
    );
    assert.equal(env.authUsers.size, 0);
  });

  test("completeTechnicianPasswordChangeHandler flips mustChangePassword to false", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);
    const created = await createTechnicianAccountHandler(
      {
        auth: { uid: "admin_uid_1", token: { email: PRIMARY_ADMIN_EMAIL, email_verified: true } },
        data: {
          workspaceId: "ws_tse_1",
          name: "First Login Tech",
          email: "firstlogin@example.com",
          initialPassword: "TempPass@123"
        }
      },
      env.deps
    );

    const updated = await completeTechnicianPasswordChangeHandler(
      {
        auth: { uid: created.uid },
        data: { workspaceId: "ws_tse_1" }
      },
      env.deps
    );
    assert.equal(updated.mustChangePassword, false);
    const memberDoc = env.store.get(`workspaces/ws_tse_1/members/${created.uid}`);
    assert.equal(memberDoc.mustChangePassword, false);
    assert.ok(memberDoc.passwordChangedAt);
  });

  test("server-authoritative 24h trash purge deletes only expired TRASHED items and writes AUTO_PURGE_24H audit log", async () => {
    const t0 = 1_760_000_000_000;
    const env = createFakeEnv({}, t0);
    seedWorkspace(env);

    // 1. Fresh (< 24h) trashed doc -> must survive
    env.store.set("workspaces/ws_tse_1/trash/tr_fresh", {
      trashId: "tr_fresh",
      entityType: "BILL",
      entityId: "b_1",
      title: "TSE-001 — Fresh Bill",
      status: "TRASHED",
      deletedAt: new env.FakeTimestamp(t0 - 10_000),
      deletedAtMillis: t0 - 10_000,
      expireAt: new env.FakeTimestamp(t0 - 10_000 + TRASH_RETENTION_MS)
    });

    // 2. Expired (> 24h) trashed doc -> must be deleted & logged
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

    // 3. Restored old doc -> untouched
    env.store.set("workspaces/ws_tse_1/trash/tr_restored", {
      trashId: "tr_restored",
      entityType: "CUSTOMER",
      entityId: "7",
      title: "Restored Customer",
      status: "RESTORED",
      deletedAt: new env.FakeTimestamp(t0 - TRASH_RETENTION_MS - 100000),
      deletedAtMillis: t0 - TRASH_RETENTION_MS - 100000,
      expireAt: new env.FakeTimestamp(t0 - 100000)
    });

    // Non-admin blocked on callable purge
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
    assert.ok(env.store.has("workspaces/ws_tse_1/trash/tr_restored"));

    const auditEntry = [...env.store.entries()].find(([k, v]) =>
      k.startsWith("workspaces/ws_tse_1/auditLogs/") && v.action === "AUTO_PURGE_24H"
    );
    assert.ok(auditEntry);
    assert.equal(auditEntry[1].entityId, "42");

    // Advance server clock past 24h for tr_fresh and trigger scheduled purge
    env.setNowMillis(t0 + TRASH_RETENTION_MS);
    const schedRes = await scheduledPurgeExpiredTrashHandler(env.deps);
    assert.equal(schedRes.purgedCount, 1);
    assert.ok(!env.store.has("workspaces/ws_tse_1/trash/tr_fresh"));
  });
});
