"use client";

import {
  GoogleAuthProvider,
  type User,
  signOut as firebaseSignOut,
  onIdTokenChanged,
  signInWithPopup,
} from "firebase/auth";
import {
  type ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { ensureFirebaseReady, getFirebase } from "./firebase";

export type AuthState = {
  user: User | null;
  idToken: string | null;
  ready: boolean;
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  /**
   * Returns the current ID token, refreshing if necessary. Used by the API
   * client so that an in-flight retry picks up a freshly minted token after
   * `onIdTokenChanged` fires.
   */
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
      const { auth } = await ensureFirebaseReady();
      if (cancelled) return;
      unsub = onIdTokenChanged(auth, async (u) => {
        setUser(u);
        if (u) {
          const token = await u.getIdToken();
          setIdToken(token);
        } else {
          setIdToken(null);
        }
        setReady(true);
      });
    })();
    return () => {
      cancelled = true;
      if (unsub) unsub();
    };
  }, []);

  const signIn = useCallback(async () => {
    const { auth } = await ensureFirebaseReady();
    const provider = new GoogleAuthProvider();
    await signInWithPopup(auth, provider);
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
    () => ({ user, idToken, ready, signIn, signOut, getIdToken }),
    [user, idToken, ready, signIn, signOut, getIdToken],
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
