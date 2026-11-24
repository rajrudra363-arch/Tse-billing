"use strict";

const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const {
  DATABASE_ID,
  createTechnicianAccountHandler,
  verifyTechnicianActivationHandler,
  regenerateTechnicianPasskeyHandler,
  completeTechnicianPasswordChangeHandler,
  purgeExpiredTrashCallableHandler,
  scheduledPurgeExpiredTrashHandler
} = require("./handler");

initializeApp();

const auth = getAuth();
const db = getFirestore(DATABASE_ID);

exports.createTechnicianAccount = onCall(
  {
    region: "asia-south1",
    cors: true
  },
  async (request) => {
    return createTechnicianAccountHandler(request, {
      auth,
      db,
      FieldValue,
      HttpsError
    });
  }
);

exports.verifyTechnicianActivation = onCall(
  {
    region: "asia-south1",
    cors: true
  },
  async (request) => {
    return verifyTechnicianActivationHandler(request, {
      auth,
      db,
      FieldValue,
      HttpsError
    });
  }
);

exports.regenerateTechnicianPasskey = onCall(
  {
    region: "asia-south1",
    cors: true
  },
  async (request) => {
    return regenerateTechnicianPasskeyHandler(request, {
      db,
      FieldValue,
      HttpsError
    });
  }
);

exports.completeTechnicianPasswordChange = onCall(
  {
    region: "asia-south1",
    cors: true
  },
  async (request) => {
    return completeTechnicianPasswordChangeHandler(request, {
      db,
      FieldValue,
      HttpsError
    });
  }
);

exports.purgeExpiredTrash = onCall(
  {
    region: "asia-south1",
    cors: true
  },
  async (request) => {
    return purgeExpiredTrashCallableHandler(request, {
      db,
      Timestamp,
      HttpsError
    });
  }
);

exports.scheduledPurgeExpiredTrash = onSchedule(
  {
    schedule: "every 60 minutes",
    region: "asia-south1"
  },
  async () => {
    return scheduledPurgeExpiredTrashHandler({
      db,
      Timestamp
    });
  }
);
