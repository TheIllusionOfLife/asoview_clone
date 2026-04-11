"use client";

import { useRouter } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth";
import { sanitizeNext } from "@/lib/redirect";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";

function SignInInner() {
  const router = useRouter();
  const params = useSearchParams();
  const next = sanitizeNext(params.get("next"));
  const { user, ready, signIn, signInWithEmail } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  useEffect(() => {
    if (ready && user) {
      router.replace(next);
    }
  }, [ready, user, next, router]);

  const onGoogleClick = useCallback(async () => {
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

  const onEmailSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setError(null);
      setPending(true);
      try {
        await signInWithEmail(email, password);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Sign-in failed");
      } finally {
        setPending(false);
      }
    },
    [signInWithEmail, email, password],
  );

  return (
    <main className="mx-auto max-w-sm space-y-6 p-6">
      <h1 className="text-2xl font-bold">Sign in</h1>

      <button
        type="button"
        onClick={onGoogleClick}
        disabled={pending}
        className="w-full rounded-lg bg-white px-4 py-3 font-medium text-gray-800 shadow hover:shadow-md disabled:opacity-50"
      >
        {pending ? "Signing in…" : "Continue with Google"}
      </button>

      <div className="flex items-center gap-3">
        <hr className="flex-1 border-gray-600" />
        <span className="text-sm text-gray-400">or</span>
        <hr className="flex-1 border-gray-600" />
      </div>

      <form onSubmit={onEmailSubmit} className="space-y-3">
        <label htmlFor="signin-email" className="sr-only">
          Email
        </label>
        <input
          id="signin-email"
          type="email"
          placeholder="Email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          className="w-full rounded-lg border border-gray-600 bg-transparent px-4 py-2"
          data-testid="email-input"
        />
        <label htmlFor="signin-password" className="sr-only">
          Password
        </label>
        <input
          id="signin-password"
          type="password"
          placeholder="Password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          className="w-full rounded-lg border border-gray-600 bg-transparent px-4 py-2"
          data-testid="password-input"
        />
        <button
          type="submit"
          disabled={pending}
          className="w-full rounded-lg bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          Sign in with Email
        </button>
      </form>

      {error ? (
        <p role="alert" className="text-sm text-red-500">
          {error}
        </p>
      ) : null}
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
