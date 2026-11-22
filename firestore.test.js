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
});
