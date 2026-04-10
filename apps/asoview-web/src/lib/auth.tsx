"use client";

import {
  GoogleAuthProvider,
  type User,
  signOut as firebaseSignOut,
  onIdTokenChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
} from "firebase/auth";
import {
  type ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
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
  signInWithEmail: (email: string, password: string) => Promise<void>;
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
  // Tracks the last uid we observed from Firebase, OUTSIDE of React state so
  // the identity-change detection runs exactly once per transition even under
  // Strict Mode (which double-invokes state updaters in dev). Keeping the
  // side-effect (`resetFavoritesCache`) out of `setUser`'s updater keeps the
  // updater pure.
  const lastUidRef = useRef<string | null>(null);

  useEffect(() => {
    let unsub: (() => void) | undefined;
    let cancelled = false;
    (async () => {
      try {
        const { auth } = await ensureFirebaseReady();
        if (cancelled) return;
        unsub = onIdTokenChanged(auth, async (u) => {
          // Reset per-user cached state on every identity transition:
          // sign-in (null → user), sign-out (user → null), and account
          // switch (userA → userB). The check uses an external ref so
          // it fires exactly once per real transition, not once per
          // Strict Mode updater invocation.
          const nextUid = u?.uid ?? null;
          if (lastUidRef.current !== nextUid) {
            resetFavoritesCache();
            lastUidRef.current = nextUid;
          }
          setUser(u);
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

  // Wire the API client's token getter to this provider so that authed
  // requests pick up `idToken` (and refreshes) without an import-time cycle.
  useEffect(() => {
    setIdTokenGetter(getIdToken);
    return () => {
      setIdTokenGetter(async () => null);
    };
  }, [getIdToken]);

  const value = useMemo<AuthState>(
    () => ({ user, idToken, ready, signIn, signInWithEmail, signOut, getIdToken }),
    [user, idToken, ready, signIn, signInWithEmail, signOut, getIdToken],
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
