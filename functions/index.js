"use strict";

const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { onCall, onRequest, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const {
  DATABASE_ID,
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
  handlePaymentWebhookHandler,
  initiateUpiPaymentHandler,
  cancelUpiPaymentHandler,
  finalizeBillWithAtomicStockHandler
} = require("./handler-payment");

initializeApp();

const auth = getAuth();
const db = getFirestore(DATABASE_ID);

const opts = { region: "asia-south1", cors: true };

exports.requestEmailOtp = onCall(opts, (request) =>
  requestEmailOtpHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.verifyEmailOtp = onCall(opts, (request) =>
  verifyEmailOtpHandler(request, { db, FieldValue, Timestamp, HttpsError })
);

exports.registerCompanyAdmin = onCall(opts, (request) =>
  registerCompanyAdminHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.verifyAdminSecondFactor = onCall(opts, (request) =>
  verifyAdminSecondFactorHandler(request, { db, FieldValue, Timestamp, HttpsError })
);

exports.resetPasswordWithOtp = onCall(opts, (request) =>
  resetPasswordWithOtpHandler(request, { auth, db, Timestamp, HttpsError })
);

exports.createTechnicianAccount = onCall(opts, (request) =>
  createTechnicianAccountHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.verifyTechnicianActivation = onCall(opts, (request) =>
  verifyTechnicianActivationHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.completeTechnicianFirstLogin = onCall(opts, (request) =>
  completeTechnicianFirstLoginHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.regenerateTechnicianPasskey = onCall(opts, (request) =>
  regenerateTechnicianPasskeyHandler(request, { db, FieldValue, HttpsError })
);

exports.completeTechnicianPasswordChange = onCall(opts, (request) =>
  completeTechnicianPasswordChangeHandler(request, { db, FieldValue, HttpsError })
);

exports.setTechnicianLoginStatus = onCall(opts, (request) =>
  setTechnicianLoginStatusHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.verifyMemberLoginAccess = onCall(opts, (request) =>
  verifyMemberLoginAccessHandler(request, { auth, db, HttpsError })
);

exports.beginPasskeyRegistration = onCall(opts, (request) =>
  beginPasskeyRegistrationHandler(request, { db, FieldValue, Timestamp, HttpsError })
);

exports.finishPasskeyRegistration = onCall(opts, (request) =>
  finishPasskeyRegistrationHandler(request, { db, FieldValue, Timestamp, HttpsError })
);

exports.beginPasskeyAuthentication = onCall(opts, (request) =>
  beginPasskeyAuthenticationHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.finishPasskeyAuthentication = onCall(opts, (request) =>
  finishPasskeyAuthenticationHandler(request, { auth, db, FieldValue, Timestamp, HttpsError })
);

exports.purgeExpiredTrash = onCall(opts, (request) =>
  purgeExpiredTrashCallableHandler(request, { db, Timestamp, HttpsError })
);

exports.scheduledPurgeExpiredTrash = onSchedule(
  { schedule: "every 60 minutes", region: "asia-south1" },
  () => scheduledPurgeExpiredTrashHandler({ db, Timestamp })
);

exports.paymentWebhook = onRequest(opts, async (req, res) => {
  try {
    const result = await handlePaymentWebhookHandler(req, { db, FieldValue, Timestamp, HttpsError });
    res.status(200).json(result);
  } catch (err) {
    const statusCode = err && err.code === "permission-denied" ? 403 : 500;
    console.error("Payment webhook error:", err);
    res.status(statusCode).json({ error: err.message || "Webhook verification failed" });
  }
});

exports.initiateUpiPayment = onCall(opts, (request) =>
  initiateUpiPaymentHandler(request, { db, FieldValue, Timestamp, HttpsError })
);

exports.cancelUpiPayment = onCall(opts, (request) =>
  cancelUpiPaymentHandler(request, { db, FieldValue, Timestamp, HttpsError })
);

exports.finalizeBillWithAtomicStock = onCall(opts, (request) =>
  finalizeBillWithAtomicStockHandler(request, { db, FieldValue, Timestamp, HttpsError })
);
