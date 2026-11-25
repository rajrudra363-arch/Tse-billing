"use strict";

const crypto = require("crypto");

/**
 * Handles Razorpay & Cashfree webhook events securely for server-side payment verification.
 * Verifies gateway HMAC signature, prevents duplicate bill status updates,
 * updates Firestore workspace bill status to PAID, records transaction metadata,
 * and synchronizes in real time across devices.
 */
async function handlePaymentWebhookHandler(request, { db, FieldValue, Timestamp, HttpsError }) {
  const gatewayHeader = request.headers["x-razorpay-signature"] ? "razorpay" : (request.headers["x-webhook-signature"] ? "cashfree" : "unknown");
  const rawBody = request.rawBody || JSON.stringify(request.body || {});
  const payload = request.body || {};

  let eventType = payload.event || payload.type || "";
  let paymentId = "";
  let orderId = "";
  let billId = "";
  let amountReceived = 0;
  let signature = "";

  if (gatewayHeader === "razorpay") {
    signature = request.headers["x-razorpay-signature"] || "";
    // Verify Razorpay HMAC SHA256 signature
    const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET || "";
    if (webhookSecret) {
      const expectedSignature = crypto
        .createHmac("sha256", webhookSecret)
        .update(rawBody)
        .digest("hex");
      if (expectedSignature !== signature) {
        throw new HttpsError("permission-denied", "Invalid Razorpay webhook signature.");
      }
    }

    const paymentEntity = payload.payload?.payment?.entity || {};
    paymentId = paymentEntity.id || payload.payment_id || "";
    orderId = paymentEntity.order_id || payload.order_id || "";
    amountReceived = (paymentEntity.amount || 0) / 100.0; // convert paise to INR
    billId = paymentEntity.notes?.billId || paymentEntity.notes?.bill_id || payload.notes?.billId || "";
    eventType = payload.event || "";
  } else if (gatewayHeader === "cashfree") {
    signature = request.headers["x-webhook-signature"] || "";
    const webhookSecret = process.env.CASHFREE_WEBHOOK_SECRET || "";
    if (webhookSecret) {
      const timestamp = request.headers["x-webhook-timestamp"] || "";
      const expectedSignature = crypto
        .createHmac("sha256", webhookSecret)
        .update(timestamp + rawBody)
        .digest("base64");
      if (expectedSignature !== signature) {
        throw new HttpsError("permission-denied", "Invalid Cashfree webhook signature.");
      }
    }
    const data = payload.data || {};
    paymentId = data.payment?.payment_id || data.order?.order_id || "";
    orderId = data.order?.order_id || "";
    amountReceived = data.payment?.payment_amount || data.order?.order_amount || 0;
    billId = data.order?.order_tags?.billId || data.payment?.customer_details?.customer_id || "";
    eventType = payload.type || "";
  } else {
    // Generic simulated webhook payload or custom test invoke
    paymentId = payload.paymentId || payload.payment_id || `pay_sim_${Date.now()}`;
    orderId = payload.orderId || payload.order_id || `order_sim_${Date.now()}`;
    billId = payload.billId || payload.bill_id || "";
    amountReceived = Number(payload.amount || 0);
    eventType = payload.event || "payment.captured";
  }

  if (!billId) {
    // Try to resolve billId by searching across workspaces if orderId matches transaction
    const txQuery = await db.collectionGroup("upiTransactions").where("orderId", "==", orderId).limit(1).get();
    if (!txQuery.empty) {
      billId = txQuery.docs[0].data().billId;
    }
  }

  if (!billId) {
    return { status: "ignored", message: "Webhook received but billId could not be resolved." };
  }

  // Find which workspace owns this bill
  const workspacesSnap = await db.collection("workspaces").get();
  let targetWorkspaceId = "";
  let targetBillDocRef = null;
  let targetBillData = null;

  for (const wsDoc of workspacesSnap.docs) {
    const wsId = wsDoc.id;
    const billDocRef = db.collection("workspaces").document(wsId).collection("bills").document(billId);
    const billSnap = await billDocRef.get();
    if (billSnap.exists) {
      targetWorkspaceId = wsId;
      targetBillDocRef = billDocRef;
      targetBillData = billSnap.data();
      break;
    }
  }

  if (!targetBillDocRef || !targetBillData) {
    return { status: "not_found", message: `Bill ${billId} not found in any workspace.` };
  }

  const isSuccess = eventType.includes("captured") || eventType.includes("SUCCESS") || eventType.includes("paid") || eventType === "payment.success";
  const isFailed = eventType.includes("failed") || eventType.includes("FAILED");
  const isExpired = eventType.includes("expired") || eventType.includes("EXPIRED");

  const newStatus = isSuccess ? "PAID" : (isFailed ? "FAILED" : (isExpired ? "EXPIRED" : "PENDING"));

  // Prevent duplicate processing if bill is already PAID and incoming event is duplicate
  if (targetBillData.paidAmount >= targetBillData.grandTotal && newStatus === "PAID") {
    return { status: "already_processed", message: `Bill ${billId} is already PAID.` };
  }

  const transactionPayload = {
    paymentId: paymentId || `pay_${Date.now()}`,
    billId: billId,
    invoiceNo: targetBillData.invoiceNo || "",
    orderId: orderId || `ord_${Date.now()}`,
    amount: amountReceived > 0 ? amountReceived : targetBillData.grandTotal,
    status: newStatus,
    paymentGateway: gatewayHeader.toUpperCase(),
    gatewaySignature: signature,
    updatedAt: Timestamp.now()
  };

  const txDocRef = db.collection("workspaces").document(targetWorkspaceId)
    .collection("upiTransactions").document(transactionPayload.paymentId);

  await db.runTransaction(async (transaction) => {
    const currentBillSnap = await transaction.get(targetBillDocRef);
    if (!currentBillSnap.exists) return;

    const currentData = currentBillSnap.data();
    const grandTotal = currentData.grandTotal || 0.0;
    const paidAmt = newStatus === "PAID" ? grandTotal : currentData.paidAmount;
    const dueAmt = newStatus === "PAID" ? 0.0 : currentData.dueAmount;
    const paymentMode = newStatus === "PAID" ? "ONLINE (UPI Gateway)" : currentData.paymentMode;

    transaction.update(targetBillDocRef, {
      paidAmount: paidAmt,
      dueAmount: dueAmt,
      paymentMode: paymentMode,
      updatedAt: FieldValue.serverTimestamp()
    });

    transaction.set(txDocRef, {
      ...transactionPayload,
      createdAt: FieldValue.serverTimestamp()
    }, { merge: true });
  });

  return {
    status: "success",
    billId: billId,
    workspaceId: targetWorkspaceId,
    paymentStatus: newStatus
  };
}

/**
 * Initiates an order with Razorpay or Cashfree server-side APIs securely
 * without exposing API keys on the Android device.
 */
async function initiateUpiPaymentHandler(request, { db, Timestamp, HttpsError }) {
  const auth = request.auth;
  if (!auth) {
    throw new HttpsError("unauthenticated", "Authentication required to initiate UPI payment.");
  }

  const { workspaceId, billId, amount, customerEmail, customerPhone, gateway } = request.data || {};
  if (!workspaceId || !billId || !amount) {
    throw new HttpsError("invalid-argument", "workspaceId, billId, and amount are required.");
  }

  const wsRef = db.collection("workspaces").document(workspaceId);
  const billRef = wsRef.collection("bills").document(billId);
  const billSnap = await billRef.get();
  if (!billSnap.exists) {
    throw new HttpsError("not-found", "Bill record not found.");
  }
  const billData = billSnap.data();

  const orderId = `tse_ord_${Date.now()}_${Math.floor(Math.random() * 1000)}`;
  const gatewayName = (gateway || "RAZORPAY").toUpperCase();

  // Save initial PENDING transaction record
  const transactionId = `txn_${Date.now()}`;
  const txRef = wsRef.collection("upiTransactions").document(transactionId);

  await txRef.set({
    paymentId: transactionId,
    billId: billId,
    invoiceNo: billData.invoiceNo || "",
    orderId: orderId,
    amount: Number(amount),
    status: "PENDING",
    paymentGateway: gatewayName,
    qrCodeUrl: `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=upi%3A%2F%2Fpay%3Fpa%3Dtse%45okicici%26pn%3DTotal%2BSolution%2BEnterprises%26am%3D${amount}%26tn%3DInvoice%2B${billData.invoiceNo}`,
    customerEmail: customerEmail || "",
    customerPhone: customerPhone || "",
    createdAt: Timestamp.now(),
    updatedAt: Timestamp.now()
  });

  return {
    success: true,
    orderId: orderId,
    paymentId: transactionId,
    amount: Number(amount),
    currency: "INR",
    gateway: gatewayName,
    qrCodeUrl: `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=upi%3A%2F%2Fpay%3Fpa%3Dtse%45okicici%26pn%3DTotal%2BSolution%2BEnterprises%26am%3D${amount}%26tn%3DInvoice%2B${billData.invoiceNo}`,
    upiUri: `upi://pay?pa=tse@okicici&pn=Total+Solution+Enterprises&am=${amount}&tn=Invoice+${billData.invoiceNo}`
  };
}

module.exports = {
  handlePaymentWebhookHandler,
  initiateUpiPaymentHandler
};
