import Constants from "expo-constants";
import { type FirebaseApp, getApp, getApps, initializeApp } from "firebase/app";
import { type Auth, getAuth } from "firebase/auth";

// Loaded from `app.config.js` / `EXPO_PUBLIC_FIREBASE_*` env. Pre-flight
// fails fast if any required field is missing — we never fall back to
// an empty config, because a half-initialized Firebase silently accepts
// any credential as "invalid" and breaks the auth gate.
const cfg = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN,
  projectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID,
  appId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID,
};

let _app: FirebaseApp | null = null;
let _auth: Auth | null = null;

export function firebaseApp(): FirebaseApp {
  if (_app) return _app;
  for (const [k, v] of Object.entries(cfg)) {
    if (!v) {
      throw new Error(
        `Missing Firebase config env var EXPO_PUBLIC_FIREBASE_${k
          .replace(/([A-Z])/g, "_$1")
          .toUpperCase()}`,
      );
    }
  }
  _app = getApps().length ? getApp() : initializeApp(cfg as Record<string, string>);
  return _app;
}

export function firebaseAuth(): Auth {
  if (_auth) return _auth;
  // Default in-memory persistence: the operator re-authenticates on every app launch,
  // aligning with the threat model's "short-lived token, hard logout" posture.
  _auth = getAuth(firebaseApp());
  return _auth;
}

export function apiBaseUrl(): string {
  const url =
    process.env.EXPO_PUBLIC_API_BASE_URL ||
    (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl;
  if (!url) {
    throw new Error("EXPO_PUBLIC_API_BASE_URL is required");
  }
  return url;
}
