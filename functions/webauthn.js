"use strict";

const crypto = require("node:crypto");

function base64UrlDecode(str) {
  if (typeof str !== "string" || !str.length) return Buffer.alloc(0);
  const padLen = (4 - (str.length % 4)) % 4;
  const b64 = str.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat(padLen);
  return Buffer.from(b64, "base64");
}

function base64UrlEncode(buf) {
  return Buffer.from(buf).toString("base64url");
}

/**
 * Minimal deterministic CBOR decoder covering unsigned/negative ints, byte strings,
 * text strings, arrays, maps, booleans, null. Sufficient for WebAuthn attestationObject & COSE keys.
 */
function decodeCbor(buf, state = { offset: 0 }) {
  if (state.offset >= buf.length) {
    throw new Error("Unexpected end of CBOR buffer");
  }
  const initial = buf[state.offset++];
  const major = initial >> 5;
  const addInfo = initial & 0x1f;

  function readLength(info) {
    if (info < 24) return info;
    if (info === 24) return buf[state.offset++];
    if (info === 25) {
      const v = buf.readUInt16BE(state.offset);
      state.offset += 2;
      return v;
    }
    if (info === 26) {
      const v = buf.readUInt32BE(state.offset);
      state.offset += 4;
      return v;
    }
    if (info === 27) {
      const v = Number(buf.readBigUInt64BE(state.offset));
      state.offset += 8;
      return v;
    }
    throw new Error(`Unsupported CBOR length info: ${info}`);
  }

  switch (major) {
    case 0: // unsigned int
      return readLength(addInfo);
    case 1: // negative int
      return -1 - readLength(addInfo);
    case 2: { // byte string
      const len = readLength(addInfo);
      const slice = buf.subarray(state.offset, state.offset + len);
      state.offset += len;
      return slice;
    }
    case 3: { // text string
      const len = readLength(addInfo);
      const str = buf.subarray(state.offset, state.offset + len).toString("utf8");
      state.offset += len;
      return str;
    }
    case 4: { // array
      const len = readLength(addInfo);
      const arr = [];
      for (let i = 0; i < len; i++) {
        arr.push(decodeCbor(buf, state));
      }
      return arr;
    }
    case 5: { // map
      const len = readLength(addInfo);
      const map = new Map();
      for (let i = 0; i < len; i++) {
        const k = decodeCbor(buf, state);
        const v = decodeCbor(buf, state);
        map.set(k, v);
      }
      return map;
    }
    case 6: { // tag
      readLength(addInfo);
      return decodeCbor(buf, state);
    }
    case 7: {
      if (addInfo === 20) return false;
      if (addInfo === 21) return true;
      if (addInfo === 22) return null;
      return undefined;
    }
    default:
      throw new Error(`Unsupported CBOR major type ${major}`);
  }
}

function coseKeyToPublicKeyPem(coseBytes) {
  const coseMap = decodeCbor(coseBytes);
  if (!(coseMap instanceof Map)) {
    throw new Error("Invalid COSE key structure");
  }
  const kty = coseMap.get(1);
  const alg = coseMap.get(3);

  if (kty === 2 && alg === -7) {
    // EC2 P-256 ES256
    const crv = coseMap.get(-1);
    const x = coseMap.get(-2);
    const y = coseMap.get(-3);
    if (crv !== 1 || !Buffer.isBuffer(x) || !Buffer.isBuffer(y)) {
      throw new Error("Invalid EC2 P-256 COSE key parameters");
    }
    const keyObj = crypto.createPublicKey({
      key: {
        kty: "EC",
        crv: "P-256",
        x: base64UrlEncode(x),
        y: base64UrlEncode(y)
      },
      format: "jwk"
    });
    return {
      alg,
      publicKeyPem: keyObj.export({ type: "spki", format: "pem" }).toString()
    };
  }

  if (kty === 3 && alg === -257) {
    // RSA RS256
    const n = coseMap.get(-1);
    const e = coseMap.get(-2);
    if (!Buffer.isBuffer(n) || !Buffer.isBuffer(e)) {
      throw new Error("Invalid RSA COSE key parameters");
    }
    const keyObj = crypto.createPublicKey({
      key: {
        kty: "RSA",
        n: base64UrlEncode(n),
        e: base64UrlEncode(e)
      },
      format: "jwk"
    });
    return {
      alg,
      publicKeyPem: keyObj.export({ type: "spki", format: "pem" }).toString()
    };
  }

  throw new Error(`Unsupported WebAuthn key type/alg: kty=${kty}, alg=${alg}`);
}

function parseAuthenticatorData(authDataBuf) {
  if (!Buffer.isBuffer(authDataBuf) || authDataBuf.length < 37) {
    throw new Error("Invalid authenticatorData buffer");
  }
  const rpIdHash = authDataBuf.subarray(0, 32);
  const flags = authDataBuf[32];
  const userPresent = (flags & 0x01) !== 0;
  const userVerified = (flags & 0x04) !== 0;
  const attestedCredentialData = (flags & 0x40) !== 0;
  const signCount = authDataBuf.readUInt32BE(33);

  let parsedCred = null;
  if (attestedCredentialData) {
    if (authDataBuf.length < 55) {
      throw new Error("Truncated attestedCredentialData");
    }
    const credIdLen = authDataBuf.readUInt16BE(53);
    const credIdStart = 55;
    const credIdEnd = credIdStart + credIdLen;
    if (authDataBuf.length < credIdEnd) {
      throw new Error("Truncated credentialId in authData");
    }
    const credentialIdBuf = authDataBuf.subarray(credIdStart, credIdEnd);
    const coseBytes = authDataBuf.subarray(credIdEnd);
    const { alg, publicKeyPem } = coseKeyToPublicKeyPem(coseBytes);
    parsedCred = {
      credentialId: base64UrlEncode(credentialIdBuf),
      alg,
      publicKeyPem
    };
  }

  return {
    rpIdHash,
    flags,
    userPresent,
    userVerified,
    signCount,
    attestedCredential: parsedCred
  };
}

function isAllowedOrigin(origin, rpId, allowedApkOrigins = []) {
  if (typeof origin !== "string" || !origin) return false;
  if (rpId && origin === `https://${rpId}`) return true;
  if (origin.startsWith("android:apk-key-hash:")) {
    if (allowedApkOrigins.length === 0) return true;
    return allowedApkOrigins.includes(origin);
  }
  return false;
}

function verifyRegistrationResponse({
  responseJson,
  expectedChallenge,
  rpId,
  allowedApkOrigins = []
}) {
  const payload = typeof responseJson === "string" ? JSON.parse(responseJson) : responseJson;
  const res = (payload && payload.response) || {};
  const clientDataBuf = base64UrlDecode(res.clientDataJSON || "");
  if (!clientDataBuf.length) {
    throw new Error("Missing clientDataJSON");
  }
  const clientData = JSON.parse(clientDataBuf.toString("utf8"));
  if (clientData.type !== "webauthn.create") {
    throw new Error("Invalid clientData type for registration");
  }
  if (clientData.challenge !== expectedChallenge) {
    throw new Error("WebAuthn registration challenge mismatch");
  }
  if (!isAllowedOrigin(clientData.origin, rpId, allowedApkOrigins)) {
    throw new Error(`Disallowed WebAuthn origin: ${clientData.origin}`);
  }

  const attBuf = base64UrlDecode(res.attestationObject || "");
  if (!attBuf.length) {
    throw new Error("Missing attestationObject");
  }
  const attMap = decodeCbor(attBuf);
  if (!(attMap instanceof Map)) {
    throw new Error("Invalid attestationObject CBOR map");
  }
  const authDataBuf = attMap.get("authData");
  const authData = parseAuthenticatorData(authDataBuf);

  const expectedRpHash = crypto.createHash("sha256").update(rpId).digest();
  if (!crypto.timingSafeEqual(authData.rpIdHash, expectedRpHash)) {
    throw new Error("WebAuthn RP ID hash mismatch");
  }
  if (!authData.userPresent || !authData.userVerified) {
    throw new Error("Biometric or device PIN user verification is required");
  }
  if (!authData.attestedCredential) {
    throw new Error("Attested credential public key missing");
  }

  return {
    credentialId: authData.attestedCredential.credentialId,
    alg: authData.attestedCredential.alg,
    publicKeyPem: authData.attestedCredential.publicKeyPem,
    signCount: authData.signCount,
    origin: clientData.origin
  };
}

function verifyAuthenticationResponse({
  responseJson,
  expectedChallenge,
  rpId,
  publicKeyPem,
  previousSignCount = 0,
  allowedApkOrigins = []
}) {
  const payload = typeof responseJson === "string" ? JSON.parse(responseJson) : responseJson;
  const res = (payload && payload.response) || {};
  const clientDataBuf = base64UrlDecode(res.clientDataJSON || "");
  const authDataBuf = base64UrlDecode(res.authenticatorData || "");
  const sigBuf = base64UrlDecode(res.signature || "");

  if (!clientDataBuf.length || !authDataBuf.length || !sigBuf.length) {
    throw new Error("Incomplete WebAuthn assertion payload");
  }
  const clientData = JSON.parse(clientDataBuf.toString("utf8"));
  if (clientData.type !== "webauthn.get") {
    throw new Error("Invalid clientData type for authentication");
  }
  if (clientData.challenge !== expectedChallenge) {
    throw new Error("WebAuthn authentication challenge mismatch");
  }
  if (!isAllowedOrigin(clientData.origin, rpId, allowedApkOrigins)) {
    throw new Error(`Disallowed WebAuthn origin: ${clientData.origin}`);
  }

  const authData = parseAuthenticatorData(authDataBuf);
  const expectedRpHash = crypto.createHash("sha256").update(rpId).digest();
  if (!crypto.timingSafeEqual(authData.rpIdHash, expectedRpHash)) {
    throw new Error("WebAuthn RP ID hash mismatch");
  }
  if (!authData.userPresent || !authData.userVerified) {
    throw new Error("Biometric or device PIN user verification failed");
  }
  if (previousSignCount > 0 && authData.signCount > 0 && authData.signCount <= previousSignCount) {
    throw new Error("Passkey signCount regression detected");
  }

  const clientDataHash = crypto.createHash("sha256").update(clientDataBuf).digest();
  const signedBytes = Buffer.concat([authDataBuf, clientDataHash]);
  const verifier = crypto.createVerify("SHA256");
  verifier.update(signedBytes);
  verifier.end();
  const signatureValid = verifier.verify(publicKeyPem, sigBuf);
  if (!signatureValid) {
    throw new Error("Invalid WebAuthn cryptographic signature");
  }

  return {
    verified: true,
    nextSignCount: authData.signCount,
    credentialId: payload.id || payload.rawId || ""
  };
}

module.exports = {
  base64UrlDecode,
  base64UrlEncode,
  decodeCbor,
  coseKeyToPublicKeyPem,
  parseAuthenticatorData,
  verifyRegistrationResponse,
  verifyAuthenticationResponse
};
