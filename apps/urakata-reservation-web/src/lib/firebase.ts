import { type FirebaseApp, getApps, initializeApp } from "firebase/app";
import {
  type Auth,
  browserSessionPersistence,
  connectAuthEmulator,
  getAuth,
  setPersistence,
} from "firebase/auth";

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
  persistencePromise = setPersistence(auth, browserSessionPersistence);

  const emulatorUrl = process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL;
  if (emulatorUrl) {
    connectAuthEmulator(auth, emulatorUrl, { disableWarnings: true });
  }

  runtime = { app, auth };
  return runtime;
}

export async function ensureFirebaseReady(): Promise<FirebaseRuntime> {
  const rt = getFirebase();
  if (persistencePromise) {
    await persistencePromise;
  }
  return rt;
}
