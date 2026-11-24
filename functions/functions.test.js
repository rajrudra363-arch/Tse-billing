"use strict";

const { test, describe } = require("node:test");
const assert = require("node:assert/strict");
const {
  PRIMARY_ADMIN_EMAIL,
  createTechnicianAccountHandler,
  completeTechnicianPasswordChangeHandler
} = require("./handler");

class MockHttpsError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

function createFakeEnv(initialDocs = {}) {
  const store = new Map(Object.entries(initialDocs));
  const authUsers = new Map();
  let nextUid = 1;
  let failBatchCommit = false;

  const FieldValue = {
    serverTimestamp: () => ({ _type: "serverTimestamp" }),
    arrayUnion: (...items) => ({ _type: "arrayUnion", items })
  };

  function docRef(path) {
    return {
      path,
      collection: (sub) => collectionRef(`${path}/${sub}`),
      async get() {
        const data = store.get(path);
        return {
          exists: data !== undefined,
          data: () => (data === undefined ? undefined : structuredClone(data))
        };
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
      }
    };
  }

  function collectionRef(path) {
    return {
      doc: (id) => docRef(`${path}/${id}`)
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
    deps: { auth, db, FieldValue, HttpsError: MockHttpsError },
    setFailBatchCommit(val) {
      failBatchCommit = val;
    }
  };
}

function seedWorkspace(env) {
  env.store.set("workspaces/ws_tse_1", {
    workspaceId: "ws_tse_1",
    ownerUid: "admin_uid_1",
    ownerEmail: PRIMARY_ADMIN_EMAIL,
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

describe("Cloud Function createTechnicianAccountHandler", () => {
  test("verified Admin creates Technician Auth user & Firestore records without saving password", async () => {
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

    const ws = env.store.get("workspaces/ws_tse_1");
    assert.ok(ws.memberUids.includes(res.uid));

    const memberDoc = env.store.get(`workspaces/ws_tse_1/members/${res.uid}`);
    assert.equal(memberDoc.role, "technician");
    assert.equal(memberDoc.mustChangePassword, true);
    assert.equal(memberDoc.email, "rahul.tech@example.com");
    assert.equal(memberDoc.workspaceId, "ws_tse_1");
    assert.equal(memberDoc.password, undefined);
    assert.equal(memberDoc.initialPassword, undefined);

    const accountDoc = env.store.get(`workspaces/ws_tse_1/accounts/${res.username}`);
    assert.equal(accountDoc.role, "technician");
    assert.equal(accountDoc.passwordHash, "FIREBASE_EMAIL_AUTH_ONLY");
    assert.ok(!JSON.stringify(accountDoc).includes("TempPass@123"));
    assert.ok(!JSON.stringify(memberDoc).includes("TempPass@123"));

    const authUser = env.authUsers.get(res.uid);
    assert.equal(authUser.customClaims.role, "technician");
    assert.equal(authUser.customClaims.workspaceId, "ws_tse_1");
  });

  test("rejects unauthenticated callers", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);
    await assert.rejects(
      () => createTechnicianAccountHandler({ auth: null, data: { workspaceId: "ws_tse_1", name: "Test User", email: "t@example.com", initialPassword: "TempPass@123" } }, env.deps),
      (err) => err.code === "unauthenticated"
    );
  });

  test("rejects Technician, Super Admin, and outsider callers", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);

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
  });

  test("rejects role escalation attempts (role = admin or super_admin)", async () => {
    const env = createFakeEnv();
    seedWorkspace(env);

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
});
