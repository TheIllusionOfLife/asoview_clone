"use client";

import { useAuth } from "@/lib/auth";
import { sanitizeNext } from "@/lib/redirect";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";

function SignInInner() {
  const router = useRouter();
  const params = useSearchParams();
  const next = sanitizeNext(params.get("next"));
  const { user, ready, signIn } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (ready && user) {
      router.replace(next);
    }
  }, [ready, user, next, router]);

  const onClick = useCallback(async () => {
    setError(null);
    setPending(true);
    try {
      await signIn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign-in failed");
    } finally {
      setPending(false);
    }
  }, [signIn]);

  return (
    <main>
      <h1>Sign in</h1>
      <p>Sign in with Google to continue.</p>
      <button type="button" onClick={onClick} disabled={pending}>
        {pending ? "Signing in…" : "Continue with Google"}
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </main>
  );
}

export default function SignInPage() {
  return (
    <Suspense fallback={<main>Loading…</main>}>
      <SignInInner />
    </Suspense>
  );
}
