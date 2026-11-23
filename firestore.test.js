const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read or write user profile or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("login_sessions").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      sessionAlertsEnabled: true,
      lastLoginAt: past,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("UserProfile: owner create, login sync update, profile edit update, and negative guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Valid create matching Kotlin payload (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Pratama",
      email: "alice@example.com",
      photoUrl: "https://example.com/avatar.jpg",
      sessionAlertsEnabled: true,
      lastLoginAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Action 1: Login sync partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Pratama",
      email: "alice@example.com",
      lastLoginAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Action 2: Profile edit partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice P.",
      bio: "Android Engineer",
      phoneLabel: "+62 812-0000",
      sessionAlertsEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject oversized bio (> 200 chars)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "x".repeat(201),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Future",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("UserProfile: cross-user isolation blocks Bob from reading or updating Alice", async () => {
  const past = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      sessionAlertsEnabled: true,
      lastLoginAt: past,
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      userId: BOB_UID,
      displayName: "Spoof",
      email: "bob@example.com",
      sessionAlertsEnabled: true,
      lastLoginAt: past,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("LoginSession subcollection: owner CRUD, terminal lock, and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const sessionsRef = aliceDb.collection("users").doc(ALICE_UID).collection("login_sessions");

  // Empty list query succeeds for owner
  await assertSucceeds(sessionsRef.orderBy("createdAt", "desc").get());

  // Valid create
  await assertSucceeds(
    sessionsRef.doc("sess_001").set({
      userId: ALICE_UID,
      deviceName: "Pixel 9",
      osVersion: "Android 15",
      authProvider: "google",
      sessionNote: "Masuk interaktif",
      status: "active",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject extra `id` field
  await assertFails(
    sessionsRef.doc("sess_002").set({
      id: "sess_002",
      userId: ALICE_UID,
      deviceName: "Pixel 9",
      osVersion: "Android 15",
      authProvider: "google",
      status: "active",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid update: verify session
  await assertSucceeds(
    sessionsRef.doc("sess_001").update({
      status: "verified",
      sessionNote: "Perangkat terpercaya",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Transition to terminal signed_out state
  await assertSucceeds(
    sessionsRef.doc("sess_001").update({
      status: "signed_out",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: subsequent update rejected
  await assertFails(
    sessionsRef.doc("sess_001").update({
      status: "active",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user isolation on subcollection
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("login_sessions").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("login_sessions").doc("sess_001").delete()
  );

  // Owner delete succeeds
  await assertSucceeds(sessionsRef.doc("sess_001").delete());
});
