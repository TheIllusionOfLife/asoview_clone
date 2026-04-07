import { type FirebaseApp, getApps, initializeApp } from "firebase/app";
import {
  type Auth,
  connectAuthEmulator,
  getAuth,
  inMemoryPersistence,
  setPersistence,
} from "firebase/auth";

/**
 * Firebase web initialization for Asoview!.
 *
 * Persistence is forced to in-memory so the ID token never lands on
 * disk-backed storage (localStorage / IndexedDB). This bounds XSS blast
 * radius. Token is held only in the JS heap and the in-memory cache
 * inside `AuthProvider`.
 *
 * When `NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL` is set, the Auth SDK is
 * pointed at the local emulator (used for `bun run dev` and Playwright
 * CI). Otherwise the SDK talks to real Identity Platform.
 */

type FirebaseRuntime = {
  app: FirebaseApp;
  auth: Auth;
};

let runtime: FirebaseRuntime | null = null;
let persistencePromise: Promise<void> | null = null;

function readConfig() {
  const apiKey = process.env.NEXT_PUBLIC_FIREBASE_API_KEY;
  const authDomain = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN;
  const projectId = process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID;
  const appId = process.env.NEXT_PUBLIC_FIREBASE_APP_ID;
  if (!apiKey || !authDomain || !projectId || !appId) {
    throw new Error(
      "Firebase web config missing. Set NEXT_PUBLIC_FIREBASE_API_KEY / AUTH_DOMAIN / PROJECT_ID / APP_ID.",
    );
  }
  return { apiKey, authDomain, projectId, appId };
}

export function getFirebase(): FirebaseRuntime {
  if (runtime) return runtime;
  const config = readConfig();
  const app = getApps()[0] ?? initializeApp(config);
  const auth = getAuth(app);

  // Force in-memory persistence. Note: setPersistence is async, so we
  // capture the promise and let `ensureFirebaseReady` await it before any
  // sign-in flow runs. The synchronous `auth` object is still returned for
  // listener wiring (`onIdTokenChanged`).
  persistencePromise = setPersistence(auth, inMemoryPersistence);

  const emulatorUrl = process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL;
  if (emulatorUrl) {
    connectAuthEmulator(auth, emulatorUrl, { disableWarnings: true });
  }

  runtime = { app, auth };
  return runtime;
}

/**
 * Await before any sign-in / token operation. Guarantees that
 * `setPersistence(auth, inMemoryPersistence)` has resolved so we never
 * accidentally fall back to the SDK's default localStorage persistence.
 */
export async function ensureFirebaseReady(): Promise<FirebaseRuntime> {
  const rt = getFirebase();
  if (persistencePromise) {
    await persistencePromise;
  }
  return rt;
}
