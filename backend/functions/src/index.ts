import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

// Endpoint to verify device pairing challenge & store public keys
export const verifyDevicePairing = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const { cameraDeviceId, viewerDeviceId, cameraPublicKey, signature } = data;

  if (!cameraDeviceId || !viewerDeviceId || !cameraPublicKey) {
    throw new functions.https.HttpsError("invalid-argument", "Missing required pairing parameters.");
  }

  const pairingRef = admin.firestore().collection("pairings").doc(`${cameraDeviceId}_${viewerDeviceId}`);
  await pairingRef.set({
    cameraDeviceId,
    viewerDeviceId,
    cameraPublicKey,
    signature: signature || null,
    pairedAt: admin.firestore.FieldValue.serverTimestamp(),
    status: "ACTIVE"
  });

  return { success: true, pairingId: pairingRef.id };
});

// Endpoint to issue short-lived session token
export const requestSessionToken = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "User must be authenticated.");
  }

  const { cameraDeviceId, viewerDeviceId } = data;

  const pairingDoc = await admin.firestore().collection("pairings").doc(`${cameraDeviceId}_${viewerDeviceId}`).get();
  if (!pairingDoc.exists || pairingDoc.data()?.status !== "ACTIVE") {
    throw new functions.https.HttpsError("permission-denied", "Devices are not paired.");
  }

  const sessionId = admin.firestore().collection("sessions").doc().id;
  const expiresAt = Date.now() + 60 * 1000; // 60s TTL

  await admin.firestore().collection("sessions").doc(sessionId).set({
    cameraDeviceId,
    viewerDeviceId,
    issuedTo: context.auth.uid,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    expiresAt,
    status: "INITIATED"
  });

  return { sessionId, expiresAt };
});
