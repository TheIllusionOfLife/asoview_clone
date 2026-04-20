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

  useEffect(() => {
    setIdTokenGetter(getIdToken);
  }, [getIdToken]);

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
