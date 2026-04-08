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
import { setIdTokenGetter } from "./api";
import { resetFavoritesCache } from "./favorites-cache";
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
  // Start `ready=true` so route-protected client components render
  // immediately as signed-out (null user). Firebase's in-memory
  // persistence has no stored state to restore, so waiting for
  // `onIdTokenChanged` only produces a loading spinner; the real sign-in
  // flow still updates `user` + `idToken` when the user completes
  // Google OAuth via signInWithPopup. Real persistence-backed session
  // restore lives on the server via ID-token cookies in a later PR.
  const [ready, setReady] = useState(true);

  useEffect(() => {
    let unsub: (() => void) | undefined;
    let cancelled = false;
    (async () => {
      try {
        const { auth } = await ensureFirebaseReady();
        if (cancelled) return;
        unsub = onIdTokenChanged(auth, async (u) => {
          setUser((prev) => {
            // Reset per-user cached state on every identity transition:
            // sign-in (null → user), sign-out (user → null), and account
            // switch (userA → userB). `prev?.uid !== u?.uid` covers all
            // three cases and is a no-op on the unchanged case (identical
            // uid object refreshed via token rotation).
            if (prev?.uid !== u?.uid) {
              resetFavoritesCache();
            }
            return u;
          });
          try {
            if (u) {
              const token = await u.getIdToken();
              setIdToken(token);
            } else {
              setIdToken(null);
            }
          } catch (err) {
            // A token-fetch failure (network blip, revoked session, clock
            // skew) must NOT leave the auth provider hung in `ready=false`,
            // because every page that gates on `ready` would spin forever.
            // Drop the token and proceed as if signed-out for this paint;
            // a subsequent onIdTokenChanged tick will recover.
            console.warn("Failed to fetch Firebase ID token", err);
            setIdToken(null);
          } finally {
            setReady(true);
          }
        });
      } catch (initErr) {
        // Same failure mode, one level up: if Firebase init itself
        // throws (missing config, unreachable emulator, SDK error), every
        // page that gates on `ready` hangs forever. Mark ready with a
        // null user so route-protection logic can redirect to /signin.
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

  // Wire the API client's token getter to this provider so that authed
  // requests pick up `idToken` (and refreshes) without an import-time cycle.
  useEffect(() => {
    setIdTokenGetter(getIdToken);
    return () => {
      setIdTokenGetter(async () => null);
    };
  }, [getIdToken]);

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
