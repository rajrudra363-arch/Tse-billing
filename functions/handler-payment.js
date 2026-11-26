"use strict";

const crypto = require("node:crypto");
const { OFFICIAL_COMPANY_NAME } = require("./handler");

function safeTimingEqualUtf8(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  if (!a.length || a.length !== b.length) return false;
  return crypto.timingSafeEqual(Buffer.from(a, "utf8"), Buffer.from(b, "utf8"));
}

function nowMillisFrom(Timestamp) {
  return Timestamp && typeof Timestamp.now === "function"
    ? Timestamp.now().toMillis()
    : Date.now();
}

function computeAccurateBillPaymentStatus(grandTotal, paidAmount) {
  const total = Number(grandTotal || 0);
  const paid = Number(paidAmount || 0);
  if (total <= 0.009) return paid > 0.009 ? "PAID" : "UNPAID";
  if (paid + 0.009 >= total) return "PAID";
  if (paid > 0.009) return "PARTIAL";
  return "UNPAID";
}

function aggregateItemQuantities(items) {
  const byName = new Map();
  if (!Array.isArray(items)) return byName;
  for (const item of items) {
    const desc = String((item && (item.description || item.desc || item.productName)) || "").trim().toLowerCase();
    const qty = Number((item && item.qty) || 0);
    if (desc && Number.isFinite(qty) && qty > 0) {
      byName.set(desc, (byName.get(desc) || 0) + qty);
    }
  }
  return byName;
}

async function applyOnceOnlyStockDeductionInTx({ transaction, wsRef, itemsJson, productsSnap, stockAlreadyDeducted }) {
  if (stockAlreadyDeducted) {
    return { deducted: false, warnings: [] };
  }
  let parsedItems = [];
  try {
    parsedItems = typeof itemsJson === "string" ? JSON.parse(itemsJson || "[]") : (Array.isArray(itemsJson) ? itemsJson : []);
  } catch (_) {
    parsedItems = [];
  }
  const requested = aggregateItemQuantities(parsedItems);
  if (requested.size === 0) {
    return { deducted: true, warnings: [] };
  }

  const warnings = [];
  const productDocs = (productsSnap && productsSnap.docs) || [];
  const byLowerName = new Map();
  for (const d of productDocs) {
    const pData = d.data() || {};
    const lower = String(pData.name || "").trim().toLowerCase();
    if (lower && pData.folder !== "__COMBO_PKG__") {
      byLowerName.set(lower, { ref: d.ref, data: pData });
    }
  }

  for (const [lowerName, qtyNeeded] of requested.entries()) {
    const entry = byLowerName.get(lowerName);
    if (!entry) continue;
    const currentStock = Number(entry.data.stockQty || 0);
    const minAlert = Number(entry.data.minStockAlert || 0);
    const tracked = currentStock > 0 || minAlert > 0;
    if (!tracked) continue;
    const nextStock = Math.max(0, Number((currentStock - qtyNeeded).toFixed(4)));
    if (currentStock + 1e-6 < qtyNeeded) {
      warnings.push(`Low stock on ${entry.data.name}: had ${currentStock}, billed ${qtyNeeded}`);
    }
    transaction.update(entry.ref, { stockQty: nextStock });
  }
  return { deducted: true, warnings };
}

/**
 * Initiates a gateway-backed Dynamic UPI QR order (Razorpay / Cashfree) without ever
 * exposing gateway secret keys to Android. Fails cleanly if backend gateway secrets are absent.
 */
async function initiateUpiPaymentHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError, fetchFn } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication required to generate Payment QR.");
  }

  const data = (request && request.data) || {};
  const workspaceId = String(data.workspaceId || data.companyId || "").trim();
  const billId = String(data.billId || "").trim();
  const customerId = String(data.customerId || "").trim();
  const customerName = String(data.customerName || "Customer").trim();
  const customerDetails = String(data.customerDetails || "").trim();
  const customerEmail = String(data.customerEmail || "").trim();
  const customerPhone = String(data.customerPhone || "").trim();
  const amount = Number(data.amount || 0);
  const gatewayName = String(data.gateway || "RAZORPAY").trim().toUpperCase();
  const draftSnapshot = data.draftSnapshot && typeof data.draftSnapshot === "object" ? data.draftSnapshot : null;

  if (!workspaceId || !Number.isFinite(amount) || amount <= 0.009) {
    throw new HttpsError("invalid-argument", "Valid workspaceId/companyId and amount > 0 are required.");
  }

  const wsRef = db.collection("workspaces").doc(workspaceId);
  const wsSnap = await wsRef.get();
  if (!wsSnap.exists) {
    throw new HttpsError("not-found", "Company workspace not found.");
  }
  const wsData = wsSnap.data() || {};

  // Verify caller belongs to this company and is not disabled
  const isOwner = wsData.ownerUid === authCtx.uid;
  const memberSnap = await wsRef.collection("members").doc(authCtx.uid).get();
  if (!isOwner && !memberSnap.exists) {
    throw new HttpsError("permission-denied", "Cross-company payment initiation is prohibited.");
  }
  if (memberSnap.exists && memberSnap.data().loginEnabled === false) {
    throw new HttpsError("permission-denied", "Your login has been disabled by Admin. Please contact your Admin.");
  }

  let billData = null;
  if (billId) {
    const billSnap = await wsRef.collection("bills").doc(billId).get();
    if (!billSnap.exists) {
      throw new HttpsError("not-found", "Target bill record not found.");
    }
    billData = billSnap.data() || {};
    if (String(billData.paymentStatus || "").toUpperCase() === "PAID" || Number(billData.dueAmount || 0) <= 0.009 && Number(billData.paidAmount || 0) >= Number(billData.grandTotal || 0)) {
      throw new HttpsError("already-exists", "This bill is already PAID.");
    }
  } else if (!draftSnapshot) {
    throw new HttpsError("invalid-argument", "Either an existing billId or a billing draftSnapshot is required.");
  }

  const nowMs = nowMillisFrom(Timestamp);
  const paymentId = `pay_${nowMs}_${crypto.randomBytes(4).toString("hex")}`;
  const paymentRef = `TSREF-${nowMs.toString().slice(-6)}-${crypto.randomBytes(2).toString("hex").toUpperCase()}`;
  const invoiceNo = String((billData && billData.invoiceNo) || (draftSnapshot && draftSnapshot.invoiceNo) || paymentRef);
  const expiresAtMillis = nowMs + 15 * 60 * 1000;
  const doFetch = fetchFn || globalThis.fetch;

  let orderId = "";
  let gatewayQrPayload = "";
  let gatewayPaymentUrl = "";

  if (gatewayName === "RAZORPAY") {
    const keyId = process.env.RAZORPAY_KEY_ID || "";
    const keySecret = process.env.RAZORPAY_KEY_SECRET || "";
    const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET || "";
    if (!keyId || !keySecret || !webhookSecret) {
      throw new HttpsError(
        "failed-precondition",
        "Razorpay backend credentials (RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET) are not configured on Cloud Functions."
      );
    }
    const authHeader = "Basic " + Buffer.from(`${keyId}:${keySecret}`).toString("base64");
    const orderResp = await doFetch("https://api.razorpay.com/v1/orders", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: authHeader
      },
      body: JSON.stringify({
        amount: Math.round(amount * 100),
        currency: "INR",
        receipt: paymentRef,
        notes: {
          workspaceId,
          companyId: workspaceId,
          billId: billId || "",
          paymentId,
          paymentRef,
          customerId
        }
      })
    });
    const orderJson = await orderResp.json();
    if (!orderResp.ok || !orderJson.id) {
      throw new HttpsError("internal", `Razorpay order creation failed: ${(orderJson.error && orderJson.error.description) || "API error"}`);
    }
    orderId = orderJson.id;

    // Create Dynamic UPI QR Code via Razorpay QR Codes API
    const qrResp = await doFetch("https://api.razorpay.com/v1/payments/qr_codes", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: authHeader
      },
      body: JSON.stringify({
        type: "upi_qr",
        name: `${OFFICIAL_COMPANY_NAME} ${invoiceNo}`,
        usage: "single_use",
        fixed_amount: true,
        payment_amount: Math.round(amount * 100),
        description: `${OFFICIAL_COMPANY_NAME} Bill ${invoiceNo}`,
        close_by: Math.floor(expiresAtMillis / 1000),
        notes: {
          workspaceId,
          companyId: workspaceId,
          billId: billId || "",
          paymentId,
          paymentRef,
          orderId,
          customerId
        }
      })
    });
    const qrJson = await qrResp.json().catch(() => ({}));
    if (qrResp.ok && qrJson.image_content) {
      gatewayQrPayload = qrJson.image_content;
    } else if (wsData.upiId) {
      gatewayQrPayload = `upi://pay?pa=${encodeURIComponent(wsData.upiId)}&pn=${encodeURIComponent(wsData.payeeName || OFFICIAL_COMPANY_NAME)}&am=${amount.toFixed(2)}&cu=INR&tr=${encodeURIComponent(paymentRef)}&tn=${encodeURIComponent(`${OFFICIAL_COMPANY_NAME} ${invoiceNo}`)}`;
    } else {
      gatewayQrPayload = qrJson.short_url || `razorpay://order/${orderId}`;
    }
    gatewayPaymentUrl = qrJson.short_url || "";
  } else if (gatewayName === "CASHFREE") {
    const clientId = process.env.CASHFREE_CLIENT_ID || "";
    const clientSecret = process.env.CASHFREE_CLIENT_SECRET || "";
    const webhookSecret = process.env.CASHFREE_WEBHOOK_SECRET || "";
    if (!clientId || !clientSecret || !webhookSecret) {
      throw new HttpsError(
        "failed-precondition",
        "Cashfree backend credentials (CASHFREE_CLIENT_ID, CASHFREE_CLIENT_SECRET, CASHFREE_WEBHOOK_SECRET) are not configured on Cloud Functions."
      );
    }
    const baseUrl = process.env.CASHFREE_ENV === "PRODUCTION"
      ? "https://api.cashfree.com/pg"
      : "https://sandbox.cashfree.com/pg";
    const cfOrderId = `ts_${paymentRef.replace(/[^A-Za-z0-9_-]/g, "_")}`;
    const cfResp = await doFetch(`${baseUrl}/orders`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-version": "2023-08-01",
        "x-client-id": clientId,
        "x-client-secret": clientSecret
      },
      body: JSON.stringify({
        order_id: cfOrderId,
        order_amount: Number(amount.toFixed(2)),
        order_currency: "INR",
        customer_details: {
          customer_id: customerId || `cust_${nowMs}`,
          customer_name: customerName,
          customer_phone: customerPhone || "9999999999",
          customer_email: customerEmail || "customer@totalsolution.app"
        },
        order_tags: {
          workspaceId,
          companyId: workspaceId,
          billId: billId || "",
          paymentId,
          paymentRef,
          customerId
        }
      })
    });
    const cfJson = await cfResp.json();
    if (!cfResp.ok || !cfJson.payment_session_id) {
      throw new HttpsError("internal", `Cashfree order creation failed: ${cfJson.message || "API error"}`);
    }
    orderId = cfJson.order_id || cfOrderId;
    gatewayPaymentUrl = cfJson.payment_session_id;
    gatewayQrPayload = wsData.upiId
      ? `upi://pay?pa=${encodeURIComponent(wsData.upiId)}&pn=${encodeURIComponent(wsData.payeeName || OFFICIAL_COMPANY_NAME)}&am=${amount.toFixed(2)}&cu=INR&tr=${encodeURIComponent(paymentRef)}&tn=${encodeURIComponent(`${OFFICIAL_COMPANY_NAME} ${invoiceNo}`)}`
      : `cashfree://pay/${orderId}`;
  } else {
    throw new HttpsError("invalid-argument", "Supported gateways are RAZORPAY or CASHFREE.");
  }

  const txRef = wsRef.collection("upiTransactions").doc(paymentId);
  await txRef.set({
    paymentId,
    paymentRef,
    companyId: workspaceId,
    workspaceId,
    customerId,
    customerName,
    customerDetails,
    billId,
    invoiceNo,
    orderId,
    txId: "",
    amount: Number(amount.toFixed(2)),
    status: "PENDING",
    paymentGateway: gatewayName,
    gatewaySignature: "",
    qrCodeUrl: gatewayQrPayload,
    upiUri: gatewayQrPayload,
    paymentUrl: gatewayPaymentUrl,
    receiptNumber: "",
    verifiedByWebhook: false,
    draftSnapshot: draftSnapshot ? JSON.stringify(draftSnapshot) : "",
    customerEmail,
    customerPhone,
    createdByUid: authCtx.uid,
    createdAt: FieldValue ? FieldValue.serverTimestamp() : nowMs,
    updatedAt: FieldValue ? FieldValue.serverTimestamp() : nowMs,
    createdAtMillis: nowMs,
    expiresAtMillis
  });

  return {
    success: true,
    paymentId,
    paymentRef,
    orderId,
    billId,
    invoiceNo,
    amount: Number(amount.toFixed(2)),
    currency: "INR",
    status: "PENDING",
    gateway: gatewayName,
    upiUri: gatewayQrPayload,
    paymentUrl: gatewayPaymentUrl,
    expiresAtMillis
  };
}

/**
 * Allows cancelling an unpaid PENDING payment request safely (never marks PAID/SUCCESS).
 */
async function cancelUpiPaymentHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const { workspaceId, paymentId, nextStatus } = request.data || {};
  const allowedClientStatus = nextStatus === "EXPIRED" ? "EXPIRED" : "CANCELLED";
  if (!workspaceId || !paymentId) {
    throw new HttpsError("invalid-argument", "workspaceId and paymentId are required.");
  }
  const txRef = db.collection("workspaces").doc(workspaceId).collection("upiTransactions").doc(paymentId);
  const snap = await txRef.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "Payment transaction not found.");
  }
  const cur = snap.data() || {};
  if (cur.status === "SUCCESS" || cur.status === "PAID") {
    throw new HttpsError("failed-precondition", "Cannot cancel an already verified successful payment.");
  }
  await txRef.update({
    status: allowedClientStatus,
    updatedAt: FieldValue ? FieldValue.serverTimestamp() : nowMillisFrom(Timestamp)
  });
  return { paymentId, status: allowedClientStatus };
}

/**
 * Fail-closed Payment Gateway Webhook Handler (Razorpay & Cashfree).
 * - Rejects unsigned or unconfigured-secret requests (never accepts client trust).
 * - Uses idempotency lock `processedWebhookEvents/{eventId}`.
 * - Verifies paid amount, marks payment SUCCESS/FAILED/CANCELLED/EXPIRED,
 *   auto-generates Final Bill if initiated from draftSnapshot, and deducts stock ONCE atomically.
 */
async function handlePaymentWebhookHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const headers = (request && request.headers) || {};
  const razorpaySig = headers["x-razorpay-signature"] || headers["X-Razorpay-Signature"] || "";
  const cashfreeSig = headers["x-webhook-signature"] || headers["X-Webhook-Signature"] || "";
  const rawBody = request.rawBody
    ? (Buffer.isBuffer(request.rawBody) ? request.rawBody.toString("utf8") : String(request.rawBody))
    : JSON.stringify(request.body || {});
  const payload = request.body || {};

  let gateway = "";
  let signature = "";
  let eventType = "";
  let gatewayPaymentId = "";
  let orderId = "";
  let paymentId = "";
  let paymentRef = "";
  let billId = "";
  let workspaceId = "";
  let amountReceived = 0;

  if (razorpaySig) {
    gateway = "RAZORPAY";
    signature = String(razorpaySig);
    const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET || "";
    if (!webhookSecret) {
      throw new HttpsError("permission-denied", "RAZORPAY_WEBHOOK_SECRET is not configured on backend.");
    }
    const expected = crypto.createHmac("sha256", webhookSecret).update(rawBody).digest("hex");
    if (!safeTimingEqualUtf8(expected, signature)) {
      throw new HttpsError("permission-denied", "Invalid Razorpay webhook signature.");
    }
    const paymentEntity = (payload.payload && payload.payload.payment && payload.payload.payment.entity) || {};
    const qrEntity = (payload.payload && payload.payload.qr_code && payload.payload.qr_code.entity) || {};
    const notes = paymentEntity.notes || qrEntity.notes || payload.notes || {};
    gatewayPaymentId = String(paymentEntity.id || payload.payment_id || "");
    orderId = String(paymentEntity.order_id || notes.orderId || payload.order_id || "");
    paymentId = String(notes.paymentId || "");
    paymentRef = String(notes.paymentRef || "");
    billId = String(notes.billId || notes.bill_id || "");
    workspaceId = String(notes.workspaceId || notes.companyId || "");
    amountReceived = Number(paymentEntity.amount || 0) / 100.0;
    eventType = String(payload.event || "");
  } else if (cashfreeSig) {
    gateway = "CASHFREE";
    signature = String(cashfreeSig);
    const webhookSecret = process.env.CASHFREE_WEBHOOK_SECRET || "";
    if (!webhookSecret) {
      throw new HttpsError("permission-denied", "CASHFREE_WEBHOOK_SECRET is not configured on backend.");
    }
    const timestamp = String(headers["x-webhook-timestamp"] || headers["X-Webhook-Timestamp"] || "");
    if (!timestamp) {
      throw new HttpsError("permission-denied", "Missing Cashfree webhook timestamp header.");
    }
    const expected = crypto.createHmac("sha256", webhookSecret).update(timestamp + rawBody).digest("base64");
    if (!safeTimingEqualUtf8(expected, signature)) {
      throw new HttpsError("permission-denied", "Invalid Cashfree webhook signature.");
    }
    const data = payload.data || {};
    const tags = (data.order && data.order.order_tags) || {};
    gatewayPaymentId = String((data.payment && data.payment.cf_payment_id) || (data.payment && data.payment.payment_id) || "");
    orderId = String((data.order && data.order.order_id) || "");
    paymentId = String(tags.paymentId || "");
    paymentRef = String(tags.paymentRef || "");
    billId = String(tags.billId || "");
    workspaceId = String(tags.workspaceId || tags.companyId || "");
    amountReceived = Number((data.payment && data.payment.payment_amount) || (data.order && data.order.order_amount) || 0);
    eventType = String(payload.type || "");
  } else {
    // Fail-closed: NEVER trust unsigned webhooks or client-supplied success responses
    throw new HttpsError("permission-denied", "Missing or invalid Payment Gateway webhook signature.");
  }

  const lowerEvent = eventType.toLowerCase();
  const mappedStatus = (
    lowerEvent.includes("captured") ||
    lowerEvent.includes("success") ||
    lowerEvent.includes("credited") ||
    lowerEvent === "payment.paid"
  )
    ? "SUCCESS"
    : lowerEvent.includes("cancelled") || lowerEvent.includes("canceled")
      ? "CANCELLED"
      : lowerEvent.includes("expired") || lowerEvent.includes("closed")
        ? "EXPIRED"
        : lowerEvent.includes("failed") || lowerEvent.includes("user_dropped")
          ? "FAILED"
          : "PENDING";

  const eventKeyRaw = `${gateway}:${gatewayPaymentId || orderId || paymentId}:${eventType}`;
  const eventId = crypto.createHash("sha256").update(eventKeyRaw).digest("hex");

  // Locate transaction doc across workspace if workspaceId/paymentId not in notes
  let txDocRef = null;
  if (workspaceId && paymentId) {
    txDocRef = db.collection("workspaces").doc(workspaceId).collection("upiTransactions").doc(paymentId);
  } else if (orderId) {
    const q = await db.collectionGroup("upiTransactions").where("orderId", "==", orderId).limit(1).get();
    if (!q.empty) {
      txDocRef = q.docs[0].ref;
      const d = q.docs[0].data() || {};
      workspaceId = d.workspaceId || d.companyId || "";
      paymentId = d.paymentId || q.docs[0].id;
      billId = billId || d.billId || "";
    }
  } else if (paymentRef) {
    const q = await db.collectionGroup("upiTransactions").where("paymentRef", "==", paymentRef).limit(1).get();
    if (!q.empty) {
      txDocRef = q.docs[0].ref;
      const d = q.docs[0].data() || {};
      workspaceId = d.workspaceId || d.companyId || "";
      paymentId = d.paymentId || q.docs[0].id;
      billId = billId || d.billId || "";
    }
  }

  if (!txDocRef || !workspaceId) {
    return { status: "ignored", message: "Webhook verified but matching payment transaction not found." };
  }

  const wsRef = db.collection("workspaces").doc(workspaceId);
  const eventRef = db.collection("processedWebhookEvents").doc(eventId);
  const nowMs = nowMillisFrom(Timestamp);
  const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowMs;

  // Read products beforehand so we can atomically read/update inside transaction (compatible with mock & real Firestore)
  const productsSnap = await wsRef.collection("products").get();

  const txResult = await db.runTransaction(async (transaction) => {
    const processedSnap = await transaction.get(eventRef);
    if (processedSnap.exists) {
      return { status: "duplicate_event_ignored", paymentId, billId };
    }

    const txSnap = await transaction.get(txDocRef);
    if (!txSnap.exists) {
      return { status: "not_found", paymentId };
    }
    const txData = txSnap.data() || {};

    if (txData.status === "SUCCESS" && mappedStatus === "SUCCESS") {
      transaction.set(eventRef, { eventId, paymentId, status: "SUCCESS", processedAt: nowTs });
      return { status: "already_processed", paymentId, billId: txData.billId || billId };
    }

    let resolvedBillId = billId || txData.billId || "";
    let generatedReceiptNo = txData.receiptNumber || "";

    if (mappedStatus === "SUCCESS") {
      const expectedAmount = Number(txData.amount || 0);
      if (amountReceived > 0 && amountReceived + 0.01 < expectedAmount) {
        transaction.set(eventRef, { eventId, paymentId, status: "AMOUNT_MISMATCH", processedAt: nowTs });
        return {
          status: "amount_mismatch",
          message: `Received ${amountReceived}, expected ${expectedAmount}`
        };
      }

      generatedReceiptNo = generatedReceiptNo || `RCPT-${nowMs.toString().slice(-6)}`;
      let billRef = resolvedBillId ? wsRef.collection("bills").doc(resolvedBillId) : null;
      const existingBillSnap = billRef ? await transaction.get(billRef) : null;

      if (existingBillSnap && existingBillSnap.exists) {
        const curBill = existingBillSnap.data() || {};
        const grandTotal = Number(curBill.grandTotal || expectedAmount);
        const alreadyDeducted = Boolean(curBill.stockDeducted);
        await applyOnceOnlyStockDeductionInTx({
          transaction,
          wsRef,
          itemsJson: curBill.itemsJson,
          productsSnap,
          stockAlreadyDeducted: alreadyDeducted
        });
        transaction.update(billRef, {
          paidAmount: grandTotal,
          dueAmount: 0,
          paymentMode: "Online Payment",
          paymentStatus: "PAID",
          linkedPaymentId: paymentId,
          stockDeducted: true,
          updatedAt: nowTs
        });
      } else {
        // Automatic Final Bill creation from draftSnapshot after verified payment SUCCESS
        let draft = {};
        try {
          draft = txData.draftSnapshot ? JSON.parse(txData.draftSnapshot) : {};
        } catch (_) {
          draft = {};
        }
        resolvedBillId = resolvedBillId || `bill_${paymentId}`;
        billRef = wsRef.collection("bills").doc(resolvedBillId);
        const itemsJson = typeof draft.itemsJson === "string"
          ? draft.itemsJson
          : JSON.stringify(Array.isArray(draft.items) ? draft.items : []);
        const grandTotal = Number(draft.grandTotal || expectedAmount);
        const invoiceNo = String(draft.invoiceNo || txData.invoiceNo || `TS-${nowMs.toString().slice(-5)}`);

        await applyOnceOnlyStockDeductionInTx({
          transaction,
          wsRef,
          itemsJson,
          productsSnap,
          stockAlreadyDeducted: false
        });

        transaction.set(billRef, {
          billId: resolvedBillId,
          invoiceNo,
          date: String(draft.date || new Date(nowMs).toISOString().slice(0, 10)),
          customerName: String(draft.customerName || txData.customerName || "Customer"),
          customerDetails: String(draft.customerDetails || txData.customerDetails || ""),
          itemsJson,
          subtotal: Number(draft.subtotal || grandTotal),
          discount: Number(draft.discount || 0),
          grandTotal,
          paidAmount: grandTotal,
          dueAmount: 0,
          paymentMode: "Online Payment",
          paymentStatus: "PAID",
          linkedPaymentId: paymentId,
          stockDeducted: true,
          signatureBase64: String(draft.signatureBase64 || ""),
          customQrText: String(draft.customQrText || ""),
          createdBy: String(draft.createdBy || txData.createdByUid || "system"),
          createdByUid: String(txData.createdByUid || "system"),
          extraChargeType: String(draft.extraChargeType || "NONE"),
          extraChargeAmount: Number(draft.extraChargeAmount || 0),
          secondaryChargeType: String(draft.secondaryChargeType || "NONE"),
          secondaryChargeAmount: Number(draft.secondaryChargeAmount || 0),
          staffRating: 0,
          staffReviewNote: "",
          documentType: "BILL",
          helpBill: Boolean(draft.helpBill || false),
          helpRequestId: Number(draft.helpRequestId || 0),
          quotationNote: String(draft.quotationNote || ""),
          updatedAt: nowTs
        });
      }
    }

    transaction.update(txDocRef, {
      status: mappedStatus,
      txId: gatewayPaymentId || txData.txId || "",
      billId: resolvedBillId,
      gatewaySignature: signature,
      receiptNumber: generatedReceiptNo,
      verifiedByWebhook: true,
      ...(mappedStatus === "SUCCESS" ? { paidAtMillis: nowMs } : {}),
      updatedAt: nowTs
    });

    transaction.set(eventRef, {
      eventId,
      paymentId,
      billId: resolvedBillId,
      status: mappedStatus,
      processedAt: nowTs
    });

    return {
      status: "processed",
      workspaceId,
      paymentId,
      billId: resolvedBillId,
      paymentStatus: mappedStatus,
      receiptNumber: generatedReceiptNo
    };
  });

  if (txResult.status === "processed" && txResult.paymentStatus === "SUCCESS") {
    const notifId = `n_pay_${nowMs}`;
    await wsRef.collection("notifications").doc(notifId).set({
      notifId,
      category: "PAYMENT",
      title: "Verified UPI Payment Received",
      message: `₹${Number(amountReceived || 0).toFixed(2)} verified for Receipt ${txResult.receiptNumber}. Bill generated & stock updated.`,
      targetRole: "all",
      targetUsername: "",
      createdBy: gateway,
      createdByUid: "payment_webhook",
      createdAt: nowTs
    });
  }

  return txResult;
}

/**
 * Manual Final Bill creation/update with strict idempotent once-only stock deduction
 * and accurate paymentStatus (PAID / PARTIAL / UNPAID). Never marks unpaid bills PAID.
 */
async function finalizeBillWithAtomicStockHandler(request, deps) {
  const { db, FieldValue, Timestamp, HttpsError } = deps;
  const authCtx = request && request.auth;
  if (!authCtx || !authCtx.uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }

  const data = (request && request.data) || {};
  const workspaceId = String(data.workspaceId || data.companyId || "").trim();
  const bill = (data.bill && typeof data.bill === "object") ? data.bill : null;
  if (!workspaceId || !bill || !bill.billId) {
    throw new HttpsError("invalid-argument", "workspaceId and bill payload with billId are required.");
  }

  const docType = String(bill.documentType || "BILL").toUpperCase();
  if (!["BILL", "QUOTATION"].includes(docType)) {
    throw new HttpsError("invalid-argument", "Invalid documentType.");
  }

  const wsRef = db.collection("workspaces").doc(workspaceId);
  const wsSnap = await wsRef.get();
  if (!wsSnap.exists) {
    throw new HttpsError("not-found", "Workspace not found.");
  }
  const wsData = wsSnap.data() || {};
  const memberSnap = await wsRef.collection("members").doc(authCtx.uid).get();
  if (wsData.ownerUid !== authCtx.uid && !memberSnap.exists) {
    throw new HttpsError("permission-denied", "Cross-company billing access denied.");
  }
  if (memberSnap.exists && memberSnap.data().loginEnabled === false) {
    throw new HttpsError("permission-denied", "Your login has been disabled by Admin. Please contact your Admin.");
  }

  const grandTotal = Math.max(0, Number(bill.grandTotal || 0));
  const paidAmount = Math.max(0, Math.min(grandTotal, Number(bill.paidAmount || 0)));
  const dueAmount = Number((grandTotal - paidAmount).toFixed(2));
  const paymentStatus = computeAccurateBillPaymentStatus(grandTotal, paidAmount);
  const nowMs = nowMillisFrom(Timestamp);
  const nowTs = FieldValue ? FieldValue.serverTimestamp() : nowMs;

  const billRef = wsRef.collection("bills").doc(bill.billId);
  const productsSnap = docType === "BILL" ? await wsRef.collection("products").get() : null;

  const result = await db.runTransaction(async (transaction) => {
    const existingSnap = await transaction.get(billRef);
    const existingData = existingSnap.exists ? (existingSnap.data() || {}) : null;

    if (docType === "BILL") {
      const alreadyDeducted = Boolean(existingData && existingData.stockDeducted);
      await applyOnceOnlyStockDeductionInTx({
        transaction,
        wsRef,
        itemsJson: bill.itemsJson,
        productsSnap,
        stockAlreadyDeducted: alreadyDeducted
      });
    }

    const finalRecord = {
      billId: String(bill.billId),
      invoiceNo: String(bill.invoiceNo || `TS-${nowMs.toString().slice(-5)}`),
      date: String(bill.date || new Date(nowMs).toISOString().slice(0, 10)),
      customerName: String(bill.customerName || "Customer"),
      customerDetails: String(bill.customerDetails || ""),
      itemsJson: String(bill.itemsJson || "[]"),
      subtotal: Number(bill.subtotal || 0),
      discount: Number(bill.discount || 0),
      grandTotal,
      paidAmount,
      dueAmount,
      paymentMode: String(bill.paymentMode || "Cash"),
      paymentStatus: docType === "QUOTATION" ? "UNPAID" : paymentStatus,
      linkedPaymentId: String(bill.linkedPaymentId || (existingData && existingData.linkedPaymentId) || ""),
      // Stock is deducted strictly on BILL, never on QUOTATION
      stockDeducted: docType === "BILL",
      signatureBase64: String(bill.signatureBase64 || ""),
      customQrText: String(bill.customQrText || ""),
      createdBy: String(bill.createdBy || authCtx.uid),
      createdByUid: existingData ? String(existingData.createdByUid || authCtx.uid) : authCtx.uid,
      extraChargeType: String(bill.extraChargeType || "NONE"),
      extraChargeAmount: Number(bill.extraChargeAmount || 0),
      secondaryChargeType: String(bill.secondaryChargeType || "NONE"),
      secondaryChargeAmount: Number(bill.secondaryChargeAmount || 0),
      staffRating: Number((existingData && existingData.staffRating) || bill.staffRating || 0),
      staffReviewNote: String((existingData && existingData.staffReviewNote) || bill.staffReviewNote || ""),
      documentType: docType,
      helpBill: Boolean(bill.helpBill || false),
      helpRequestId: Number(bill.helpRequestId || 0),
      quotationNote: String(bill.quotationNote || ""),
      updatedAt: nowTs
    };

    transaction.set(billRef, finalRecord, { merge: true });
    return {
      billId: finalRecord.billId,
      paymentStatus: finalRecord.paymentStatus,
      stockDeducted: finalRecord.stockDeducted,
      paidAmount: finalRecord.paidAmount,
      dueAmount: finalRecord.dueAmount
    };
  });

  return result;
}

module.exports = {
  computeAccurateBillPaymentStatus,
  initiateUpiPaymentHandler,
  cancelUpiPaymentHandler,
  handlePaymentWebhookHandler,
  finalizeBillWithAtomicStockHandler
};
