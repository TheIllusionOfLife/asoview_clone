"use client";

import {
  signOut as firebaseSignOut,
  onIdTokenChanged,
  signInWithEmailAndPassword,
  type User,
} from "firebase/auth";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { setIdTokenGetter } from "./api";
import { ensureFirebaseReady, getFirebase } from "./firebase";

export type AuthState = {
  user: User | null;
  idToken: string | null;
  ready: boolean;
  signInWithEmail: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  getIdToken: (forceRefresh?: boolean) => Promise<string | null>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [idToken, setIdToken] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let unsub: (() => void) | undefined;
    let cancelled = false;
    (async () => {
      try {
        const { auth } = await ensureFirebaseReady();
        if (cancelled) return;
        unsub = onIdTokenChanged(auth, async (u) => {
          // Wire the API client's idTokenGetter BEFORE dispatching React
          // state updates. React runs effects depth-first (child -> parent),
          // so if the getter were wired in AuthProvider's own useEffect, a
          // descendant like VenueSelector would fire its first fetch effect
          // while the module getter still pointed at the placeholder
          // `async () => null`. The request would go out without an
          // Authorization header, Spring Security would 401, and because
          // Security's 401 bypasses the gateway's CORS filter the browser
          // would surface it as a CORS failure.
          //
          // The getter closes over `u` directly (no React state
          // dependency), so it returns the live token even if React is
          // mid-render. Doing this inside the firebase callback also keeps
          // the render body pure — safe under concurrent React and SSR,
          // since useEffect never runs on the server.
          setIdTokenGetter(
            u ? async (forceRefresh) => u.getIdToken(forceRefresh ?? false) : async () => null,
          );
          setUser(u);
          try {
            if (u) {
              const token = await u.getIdToken();
              setIdToken(token);
            } else {
              setIdToken(null);
            }
          } catch (err) {
            console.warn("Failed to fetch Firebase ID token", err);
            setIdToken(null);
          } finally {
            setReady(true);
          }
        });
      } catch (initErr) {
        if (!cancelled) {
          console.warn("Firebase init failed; proceeding as signed-out", initErr);
          setUser(null);
          setIdToken(null);
          setReady(true);
        }
      }
    })();
    return () => {
      cancelled = true;
      if (unsub) unsub();
      // Reset the module-global getter so a remount (or a different
      // provider in the same process) doesn't see a stale closure.
      setIdTokenGetter(async () => null);
    };
  }, []);

  const signInWithEmail = useCallback(async (email: string, password: string) => {
    const { auth } = await ensureFirebaseReady();
    await signInWithEmailAndPassword(auth, email, password);
  }, []);

  const signOut = useCallback(async () => {
    const { auth } = getFirebase();
    await firebaseSignOut(auth);
  }, []);

  const getIdToken = useCallback(
    async (forceRefresh = false) => {
      if (!user) return null;
      const token = await user.getIdToken(forceRefresh);
      setIdToken(token);
      return token;
    },
    [user],
  );

  const value = useMemo<AuthState>(
    () => ({ user, idToken, ready, signInWithEmail, signOut, getIdToken }),
    [user, idToken, ready, signInWithEmail, signOut, getIdToken],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth() must be called inside <AuthProvider>");
  }
  return ctx;
}
