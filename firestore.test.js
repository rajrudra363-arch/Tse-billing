const { test, describe, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require("@firebase/rules-unit-testing");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DB_ID = "ai-studio-android-tsebilli-812e5e23-5be6-4359-ba54-48c3c2747cb2";
const RULES = fs.readFileSync("./firestore.rules", "utf8");

let testEnv;

before(async () => {
  const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: RULES,
      host,
      port: Number(portStr)
    }
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function nowTs() {
  return new Date(Date.now() - 1000);
}

function validWorkspaceData(ownerUid = "owner_1") {
  const ts = nowTs();
  return {
    workspaceId: "ws_tse_1",
    ownerUid,
    ownerEmail: "owner@example.com",
    techShareCode: "TSE_TECH_991122",
    superShareCode: "TSE_SA_883344",
    companyName: "TOTAL SOLUTION ENTERPRISES",
    mobile: "7083805120",
    email: "totalsolution7020@gmail.com",
    address: "Calangute, North Goa",
    upiId: "7083805120@ybl",
    payeeName: "Manoj Kumar Saha",
    logoBase64: "",
    authorisedSignatureBase64: "",
    fallbackQrBase64: "",
    headerNote: "Welcome to TSE",
    voiceLogin: "Welcome",
    voicePostLogin: "Logged in",
    voiceBillStart: "Generating",
    voiceBillDone: "Done",
    voiceModelId: "preset_clear",
    memberUids: [ownerUid],
    createdAt: ts,
    updatedAt: ts
  };
}

function validBillData(billId = "b_1", createdBy = "rahul_tech", createdByUid = "tech_1", staffRating = 0) {
  return {
    billId,
    invoiceNo: "TSE-001",
    date: "2026-11-21",
    customerName: "Ramesh Naik",
    customerDetails: "Panaji\n9876543210",
    itemsJson: "[]",
    subtotal: 2000,
    discount: 0,
    grandTotal: 2000,
    paidAmount: 2000,
    dueAmount: 0,
    paymentMode: "Cash",
    signatureBase64: "",
    customQrText: "",
    createdBy,
    createdByUid,
    extraChargeType: "NONE",
    extraChargeAmount: 0,
    secondaryChargeType: "NONE",
    secondaryChargeAmount: 0,
    staffRating,
    staffReviewNote: "",
    documentType: "BILL",
    helpBill: false,
    helpRequestId: 0,
    quotationNote: "",
    updatedAt: nowTs()
  };
}

describe("TSE Billing Firestore Rules", () => {
  test("unauthenticated users are denied everywhere", async () => {
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore(DB_ID);
      await db.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData());
    });

    const unauthDb = testEnv.unauthenticatedContext().firestore(DB_ID);
    await assertFails(unauthDb.collection("workspaces").doc("ws_tse_1").get());
    await assertFails(unauthDb.collection("workspaceDirectories").doc("TSE_TECH_991122").get());
    await assertFails(
      unauthDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_1").set(validBillData())
    );
  });

  test("workspace owner, technician invite join, bill creation, and zero-trust boundaries", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1", {
      email: "owner@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const techDb = testEnv.authenticatedContext("tech_1", {
      email: "tech@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const saDb = testEnv.authenticatedContext("sa_1", {
      email: "sa@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const outsiderDb = testEnv.authenticatedContext("outsider_1", {
      email: "outsider@example.com",
      email_verified: true
    }).firestore(DB_ID);

    // Owner creates workspace & directory codes
    await assertSucceeds(ownerDb.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData("owner_1")));
    await assertSucceeds(
      ownerDb.collection("workspaceDirectories").doc("TSE_TECH_991122").set({
        shareCode: "TSE_TECH_991122",
        workspaceId: "ws_tse_1",
        ownerUid: "owner_1",
        role: "technician",
        companyName: "TOTAL SOLUTION ENTERPRISES",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    await assertSucceeds(
      ownerDb.collection("workspaceDirectories").doc("TSE_SA_883344").set({
        shareCode: "TSE_SA_883344",
        workspaceId: "ws_tse_1",
        ownerUid: "owner_1",
        role: "super_admin",
        companyName: "TOTAL SOLUTION ENTERPRISES",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    // Directory listing must fail (no enumeration)
    await assertFails(outsiderDb.collection("workspaceDirectories").get());

    // Outsider cannot read workspace or self-register with wrong code or admin escalation
    await assertFails(outsiderDb.collection("workspaces").doc("ws_tse_1").get());
    await assertFails(
      outsiderDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("outsider_1").set({
        userId: "outsider_1",
        email: "outsider@example.com",
        displayName: "Bad Actor",
        role: "admin",
        inviteCode: "TSE_TECH_991122",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    await assertFails(
      outsiderDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("outsider_1").set({
        userId: "outsider_1",
        email: "outsider@example.com",
        displayName: "Bad Actor",
        role: "technician",
        inviteCode: "WRONG_CODE",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    // Technician resolves invite directory & joins with valid techShareCode
    await assertSucceeds(techDb.collection("workspaceDirectories").doc("TSE_TECH_991122").get());
    await assertSucceeds(
      techDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("tech_1").set({
        userId: "tech_1",
        email: "tech@example.com",
        displayName: "Rahul Tech",
        role: "technician",
        inviteCode: "TSE_TECH_991122",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    // Technician cannot change their own role after joining
    await assertFails(
      techDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("tech_1").update({
        role: "admin",
        updatedAt: nowTs()
      })
    );

    // Technician can read workspace and save bill & customer directly to Admin's workspace
    await assertSucceeds(techDb.collection("workspaces").doc("ws_tse_1").get());
    await assertSucceeds(
      techDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_1")
        .set(validBillData("b_1", "rahul_tech", "tech_1", 0))
    );
    await assertSucceeds(
      techDb.collection("workspaces").doc("ws_tse_1").collection("customers").doc("c_1").set({
        customerId: "c_1",
        name: "Ramesh Naik",
        details: "Panaji\n9876543210",
        phone: "9876543210",
        address: "Panaji",
        locationUrl: "",
        updatedByUid: "tech_1",
        updatedAt: nowTs()
      })
    );

    // Technician CANNOT delete bill, add product, create settlement, or edit workspace settings
    await assertFails(
      techDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_1").delete()
    );
    await assertFails(
      techDb.collection("workspaces").doc("ws_tse_1").collection("products").doc("p_1").set({
        productId: "p_1",
        name: "Camera",
        unit: "Nos",
        defaultRate: 1200,
        folder: "CCTV Cameras",
        updatedAt: nowTs()
      })
    );
    await assertFails(
      techDb.collection("workspaces").doc("ws_tse_1").update({
        companyName: "HIJACKED",
        updatedAt: nowTs()
      })
    );

    // Super Admin joins with superShareCode, reads everything, CANNOT write anywhere
    await assertSucceeds(
      saDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("sa_1").set({
        userId: "sa_1",
        email: "sa@example.com",
        displayName: "Auditor",
        role: "super_admin",
        inviteCode: "TSE_SA_883344",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    await assertSucceeds(
      saDb.collection("workspaces").doc("ws_tse_1").collection("bills").get()
    );
    await assertFails(
      saDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_sa")
        .set(validBillData("b_sa", "sa1", "sa_1", 0))
    );
    await assertFails(
      saDb.collection("workspaces").doc("ws_tse_1").collection("customers").doc("c_1").update({
        name: "Modified By Super Admin",
        updatedByUid: "sa_1",
        updatedAt: nowTs()
      })
    );

    // Admin cannot delete a bill once 5-star rating is locked
    await assertSucceeds(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_rated")
        .set(validBillData("b_rated", "rahul_tech", "tech_1", 5))
    );
    await assertFails(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_rated").delete()
    );
  });

  test("bootstrapped admin requires verified rajrudra363@gmail.com email", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1", {
      email: "owner@example.com",
      email_verified: true
    }).firestore(DB_ID);
    await assertSucceeds(ownerDb.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData("owner_1")));

    const verifiedBootAdmin = testEnv.authenticatedContext("boot_1", {
      email: "rajrudra363@gmail.com",
      email_verified: true
    }).firestore(DB_ID);
    const unverifiedImposter = testEnv.authenticatedContext("imp_1", {
      email: "rajrudra363@gmail.com",
      email_verified: false
    }).firestore(DB_ID);

    await assertSucceeds(verifiedBootAdmin.collection("workspaces").doc("ws_tse_1").get());
    await assertFails(unverifiedImposter.collection("workspaces").doc("ws_tse_1").get());
  });

  test("product stock updates, expense RBAC, 24h trash retention, and immutable audit logs", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1", {
      email: "owner@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const techDb = testEnv.authenticatedContext("tech_1", {
      email: "tech@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const saDb = testEnv.authenticatedContext("sa_1", {
      email: "sa@example.com",
      email_verified: true
    }).firestore(DB_ID);

    await assertSucceeds(ownerDb.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData("owner_1")));
    await assertSucceeds(
      techDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("tech_1").set({
        userId: "tech_1",
        email: "tech@example.com",
        displayName: "Rahul Tech",
        role: "technician",
        inviteCode: "TSE_TECH_991122",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    await assertSucceeds(
      saDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("sa_1").set({
        userId: "sa_1",
        email: "sa@example.com",
        displayName: "Auditor",
        role: "super_admin",
        inviteCode: "TSE_SA_883344",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    // Product with stockQty and minStockAlert: Admin creates; Technician can ONLY adjust stockQty
    const prodRefAdmin = ownerDb.collection("workspaces").doc("ws_tse_1").collection("products").doc("p_1");
    const prodRefTech = techDb.collection("workspaces").doc("ws_tse_1").collection("products").doc("p_1");
    await assertSucceeds(prodRefAdmin.set({
      productId: "p_1",
      name: "Dome Camera 2MP",
      unit: "Nos",
      defaultRate: 1450,
      folder: "CCTV Cameras",
      stockQty: 12,
      minStockAlert: 3,
      updatedAt: nowTs()
    }));
    await assertSucceeds(prodRefTech.update({
      stockQty: 10,
      updatedAt: nowTs()
    }));
    await assertFails(prodRefTech.update({
      defaultRate: 100,
      updatedAt: nowTs()
    }));

    // Expenses: Admin CRUD; Super Admin read-only; Technician denied read/write
    const expAdmin = ownerDb.collection("workspaces").doc("ws_tse_1").collection("expenses").doc("exp_1");
    const expSa = saDb.collection("workspaces").doc("ws_tse_1").collection("expenses").doc("exp_1");
    const expTech = techDb.collection("workspaces").doc("ws_tse_1").collection("expenses").doc("exp_1");
    const expTechCreate = techDb.collection("workspaces").doc("ws_tse_1").collection("expenses").doc("exp_2");

    await assertSucceeds(expAdmin.set({
      expenseId: "exp_1",
      title: "Petrol & Site Visit",
      category: "Travel",
      amount: 450,
      date: "2026-11-22",
      note: "Calangute client",
      recordedBy: "Manoj",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      serverUpdatedAt: nowTs()
    }));
    await assertSucceeds(expSa.get());
    await assertFails(expSa.delete());
    await assertFails(expTech.get());
    await assertFails(expTechCreate.set({
      expenseId: "exp_2",
      title: "Fake",
      category: "Misc",
      amount: 100,
      date: "2026-11-22",
      note: "",
      recordedBy: "tech",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      serverUpdatedAt: nowTs()
    }));

    // Trash: Admin creates and restores; immediate delete (<24h) MUST FAIL; >24h old doc delete SUCCEEDS; non-admins denied
    const trashRefAdmin = ownerDb.collection("workspaces").doc("ws_tse_1").collection("trash").doc("tr_1");
    const trashRefSa = saDb.collection("workspaces").doc("ws_tse_1").collection("trash").doc("tr_1");
    const trashRefTech = techDb.collection("workspaces").doc("ws_tse_1").collection("trash").doc("tr_1");

    await assertSucceeds(trashRefAdmin.set({
      trashId: "tr_1",
      entityType: "BILL",
      entityId: "b_del_1",
      title: "TSE-010 — Test",
      summary: "Bill · ₹1000.00",
      payloadJson: "{}",
      deletedBy: "Manoj",
      deletedByRole: "admin",
      status: "TRASHED",
      deletedAt: nowTs(),
      deletedAtMillis: Date.now(),
      expireAt: new Date(Date.now() + 86400000),
      restoredAt: 0,
      restoredBy: ""
    }));
    await assertFails(trashRefSa.get());
    await assertFails(trashRefTech.get());
    await assertFails(trashRefAdmin.delete()); // < 24h must fail
    await assertSucceeds(trashRefAdmin.update({
      status: "RESTORED",
      restoredAt: Date.now(),
      restoredBy: "Manoj"
    }));

    // Seed an expired trash document (> 24 hours old) and verify Admin can purge it
    await testEnv.withSecurityRulesDisabled(async (ctx) => {
      const expiredAt = new Date(Date.now() - 86_405_000);
      await ctx.firestore(DB_ID).collection("workspaces").doc("ws_tse_1").collection("trash").doc("tr_expired").set({
        trashId: "tr_expired",
        entityType: "CUSTOMER",
        entityId: "99",
        title: "Old Customer",
        summary: "Saved Customer",
        payloadJson: "{}",
        deletedBy: "Manoj",
        deletedByRole: "admin",
        status: "TRASHED",
        deletedAt: expiredAt,
        deletedAtMillis: expiredAt.getTime(),
        expireAt: new Date(expiredAt.getTime() + 86400000),
        restoredAt: 0,
        restoredBy: ""
      });
    });
    await assertSucceeds(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("trash").doc("tr_expired").delete()
    );

    // Audit logs: Admin creates; Admin & Super Admin read; Technician denied; update & delete ALWAYS denied
    const logAdmin = ownerDb.collection("workspaces").doc("ws_tse_1").collection("auditLogs").doc("log_1");
    const logSa = saDb.collection("workspaces").doc("ws_tse_1").collection("auditLogs").doc("log_1");
    const logTech = techDb.collection("workspaces").doc("ws_tse_1").collection("auditLogs").doc("log_1");
    await assertSucceeds(logAdmin.set({
      logId: "log_1",
      action: "DELETE",
      entityType: "BILL",
      entityId: "b_del_1",
      title: "TSE-010 — Test",
      actorUsername: "Manoj",
      actorRole: "admin",
      details: "Moved to Trash (24h retention)",
      createdAt: Date.now(),
      serverCreatedAt: nowTs()
    }));
    await assertSucceeds(logSa.get());
    await assertFails(logTech.get());
    await assertFails(logAdmin.update({ details: "tampered" }));
    await assertFails(logAdmin.delete());
  });

  test("cloud-provisioned technician first-login password flag and strict self-update restrictions", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1", {
      email: "rajrudra363@gmail.com",
      email_verified: true
    }).firestore(DB_ID);
    const techDb = testEnv.authenticatedContext("cloud_tech_1", {
      email: "rahul.tech@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const otherTechDb = testEnv.authenticatedContext("cloud_tech_2", {
      email: "other.tech@example.com",
      email_verified: true
    }).firestore(DB_ID);

    await assertSucceeds(ownerDb.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData("owner_1")));

    // Admin provisions technician member document with mustChangePassword = true (zero passwords stored)
    const techMemberRefAdmin = ownerDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("cloud_tech_1");
    await assertSucceeds(techMemberRefAdmin.set({
      userId: "cloud_tech_1",
      email: "rahul.tech@example.com",
      displayName: "Rahul Tech",
      name: "Rahul Tech",
      username: "rahul_tech",
      phone: "9876543210",
      role: "technician",
      workspaceId: "ws_tse_1",
      inviteCode: "TSE_TECH_991122",
      mustChangePassword: true,
      authProvider: "password",
      createdByUid: "owner_1",
      createdAt: nowTs(),
      updatedAt: nowTs()
    }));

    // Never permit creating member doc with raw password field
    await assertFails(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("cloud_tech_bad").set({
        userId: "cloud_tech_bad",
        email: "bad@example.com",
        displayName: "Bad",
        role: "technician",
        inviteCode: "TSE_TECH_991122",
        password: "plaintext_forbidden",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    const techMemberRefSelf = techDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("cloud_tech_1");
    await assertSucceeds(techMemberRefSelf.get());

    // Other user cannot flip cloud_tech_1's mustChangePassword flag
    await assertFails(
      otherTechDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("cloud_tech_1").update({
        mustChangePassword: false,
        passwordChangedAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    // Technician cannot escalate role while completing first-login password change
    await assertFails(techMemberRefSelf.update({
      mustChangePassword: false,
      role: "admin",
      passwordChangedAt: nowTs(),
      updatedAt: nowTs()
    }));

    // Technician can clear mustChangePassword once (true -> false)
    await assertSucceeds(techMemberRefSelf.update({
      mustChangePassword: false,
      passwordChangedAt: nowTs(),
      updatedAt: nowTs()
    }));

    // Technician cannot toggle mustChangePassword back to true
    await assertFails(techMemberRefSelf.update({
      mustChangePassword: true,
      updatedAt: nowTs()
    }));
  });

  test("notification sync rules across admin, technician, super_admin, and outsider", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1", {
      email: "rajrudra363@gmail.com",
      email_verified: true
    }).firestore(DB_ID);
    const techDb = testEnv.authenticatedContext("tech_1", {
      email: "tech@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const saDb = testEnv.authenticatedContext("sa_1", {
      email: "sa@example.com",
      email_verified: true
    }).firestore(DB_ID);
    const outsiderDb = testEnv.authenticatedContext("outsider_1", {
      email: "outsider@example.com",
      email_verified: true
    }).firestore(DB_ID);

    await assertSucceeds(ownerDb.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData("owner_1")));
    await assertSucceeds(
      techDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("tech_1").set({
        userId: "tech_1",
        email: "tech@example.com",
        displayName: "Rahul Tech",
        role: "technician",
        inviteCode: "TSE_TECH_991122",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    await assertSucceeds(
      saDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("sa_1").set({
        userId: "sa_1",
        email: "sa@example.com",
        displayName: "Auditor",
        role: "super_admin",
        inviteCode: "TSE_SA_883344",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    const adminNotifRef = ownerDb.collection("workspaces").doc("ws_tse_1").collection("notifications").doc("n_admin_1");
    await assertSucceeds(adminNotifRef.set({
      notifId: "n_admin_1",
      category: "PRODUCT",
      title: "Product Updated",
      message: "Dome Camera price updated",
      targetRole: "all",
      targetUsername: "",
      createdBy: "Manoj",
      createdByUid: "owner_1",
      createdAt: nowTs()
    }));

    const techNotifRef = techDb.collection("workspaces").doc("ws_tse_1").collection("notifications").doc("n_tech_1");
    await assertSucceeds(techNotifRef.set({
      notifId: "n_tech_1",
      category: "BILLING",
      title: "New Bill TSE-011",
      message: "Bill created for Ramesh",
      targetRole: "admin",
      targetUsername: "",
      createdBy: "rahul_tech",
      createdByUid: "tech_1",
      createdAt: nowTs()
    }));

    // Spoofing createdByUid fails
    await assertFails(
      techDb.collection("workspaces").doc("ws_tse_1").collection("notifications").doc("n_spoof").set({
        notifId: "n_spoof",
        category: "PAYMENT",
        title: "Spoof",
        message: "Spoofed uid",
        targetRole: "all",
        targetUsername: "",
        createdBy: "Manoj",
        createdByUid: "owner_1",
        createdAt: nowTs()
      })
    );

    // Super Admin can read but CANNOT create notifications
    await assertSucceeds(saDb.collection("workspaces").doc("ws_tse_1").collection("notifications").get());
    await assertFails(
      saDb.collection("workspaces").doc("ws_tse_1").collection("notifications").doc("n_sa_1").set({
        notifId: "n_sa_1",
        category: "SYSTEM",
        title: "SA Blocked",
        message: "Should fail",
        targetRole: "all",
        targetUsername: "",
        createdBy: "sa",
        createdByUid: "sa_1",
        createdAt: nowTs()
      })
    );

    // Outsider cannot read notifications
    await assertFails(outsiderDb.collection("workspaces").doc("ws_tse_1").collection("notifications").get());

    // Notifications are append-only (update/delete denied even for Admin)
    await assertFails(adminNotifRef.update({ title: "Edited" }));
    await assertFails(adminNotifRef.delete());
  });

  test("4 help categories, quotation approval + installation date, technician installation accept/reject, and atomic runTransaction stock decrement", async () => {
    const ownerDb = testEnv.authenticatedContext("owner_1", {
      email: "rajrudra363@gmail.com",
      email_verified: true
    }).firestore(DB_ID);
    const techDb = testEnv.authenticatedContext("tech_1", {
      email: "tech@example.com",
      email_verified: true
    }).firestore(DB_ID);

    await assertSucceeds(ownerDb.collection("workspaces").doc("ws_tse_1").set(validWorkspaceData("owner_1")));
    // Reject plaintext activationPasskey on members
    await assertFails(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("tech_1").set({
        userId: "tech_1",
        email: "tech@example.com",
        displayName: "Rahul Tech",
        role: "technician",
        inviteCode: "TSE_TECH_991122",
        activationPasskey: "PLAINTEXT-FORBIDDEN",
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );
    // Allow hashed activationPasskeyHash on members
    await assertSucceeds(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("members").doc("tech_1").set({
        userId: "tech_1",
        email: "tech@example.com",
        displayName: "Rahul Tech",
        role: "technician",
        inviteCode: "TSE_TECH_991122",
        activationPasskeyHash: "a".repeat(64),
        createdAt: nowTs(),
        updatedAt: nowTs()
      })
    );

    // Seed product with stock 5
    await assertSucceeds(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("products").doc("p_1").set({
        productId: "p_1",
        name: "5MP Bullet Camera",
        unit: "Nos",
        defaultRate: 2100,
        folder: "CCTV Cameras",
        stockQty: 5,
        minStockAlert: 1,
        updatedAt: nowTs()
      })
    );

    // Technician atomic runTransaction: decrement stockQty 5 -> 3 and create bill
    await assertSucceeds(
      techDb.runTransaction(async (tx) => {
        const prodRef = techDb.collection("workspaces").doc("ws_tse_1").collection("products").doc("p_1");
        const prodSnap = await tx.get(prodRef);
        assert.equal(prodSnap.data().stockQty, 5);
        tx.update(prodRef, { stockQty: 3, updatedAt: nowTs() });
        const billRef = techDb.collection("workspaces").doc("ws_tse_1").collection("bills").doc("b_tx_1");
        tx.set(billRef, validBillData("b_tx_1", "rahul_tech", "tech_1", 0));
      })
    );

    // Technician submits Quotation Request -> Admin approves with installationDate -> Technician accepts/rejects Installation
    const helpRefTech = techDb.collection("workspaces").doc("ws_tse_1").collection("helpRequests").doc("101");
    const helpRefAdmin = ownerDb.collection("workspaces").doc("ws_tse_1").collection("helpRequests").doc("101");

    await assertSucceeds(helpRefTech.set({
      helpId: 101,
      customerName: "Suresh Kamat",
      phone: "9822001122",
      address: "Mapusa",
      locationUrl: "",
      helpDescription: "4 Camera CCTV Setup Quotation",
      category: "QUOTATION",
      quotationStatus: "PENDING",
      installationDate: "",
      installationReminderSent: false,
      rejectedReason: "",
      assignedTechnician: "",
      status: "OPEN",
      createdAt: Date.now(),
      updatedAt: Date.now(),
      acceptedBy: "",
      acceptedByUid: "",
      acceptedByName: "",
      acceptedByPhone: "",
      acceptedAt: 0,
      solvedAt: 0,
      billId: ""
    }));

    // Technician CANNOT approve a quotation or change installationDate
    await assertFails(helpRefTech.update({
      quotationStatus: "APPROVED",
      installationDate: "2026-12-01",
      updatedAt: Date.now()
    }));

    // Admin approves Quotation and converts it to INSTALLATION with installationDate
    await assertSucceeds(helpRefAdmin.update({
      category: "INSTALLATION",
      quotationStatus: "APPROVED",
      installationDate: "2026-12-01",
      updatedAt: Date.now()
    }));

    // Technician rejects installation with reason (allowed)
    await assertSucceeds(helpRefTech.update({
      status: "REJECTED",
      rejectedReason: "Customer rescheduled site visit",
      updatedAt: Date.now()
    }));

    // Quotation/Installation notifications work
    await assertSucceeds(
      ownerDb.collection("workspaces").doc("ws_tse_1").collection("notifications").doc("n_install_1").set({
        notifId: "n_install_1",
        category: "INSTALLATION",
        title: "Installation Scheduled",
        message: "Suresh Kamat scheduled for 2026-12-01",
        targetRole: "technician",
        targetUsername: "",
        createdBy: "Manoj",
        createdByUid: "owner_1",
        createdAt: nowTs()
      })
    );
  });
});


