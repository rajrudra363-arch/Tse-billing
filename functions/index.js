"use strict";

const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const {
  DATABASE_ID,
  createTechnicianAccountHandler,
  completeTechnicianPasswordChangeHandler
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
