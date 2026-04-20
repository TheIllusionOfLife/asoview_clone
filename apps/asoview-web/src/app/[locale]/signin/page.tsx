"use client";

import { useRouter } from "@/i18n/navigation";
import { useAuth } from "@/lib/auth";
import { sanitizeNext } from "@/lib/redirect";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useState } from "react";

// Google sign-in is gated behind an explicit build-time flag because the
// Terraform-managed Identity Platform config in
// infra/terraform/modules/identity-platform/main.tf still carries
// PLACEHOLDER_OAUTH_CLIENT_ID / PLACEHOLDER_OAUTH_CLIENT_SECRET. Until a
// real OAuth 2.0 Web Client + consent-screen is wired (see
// docs/operations/google-oauth-setup.md), hitting the button returns
// `Firebase: Error (auth/internal-error)` which reads as a broken
// product. Flip NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN=true in the deploy
// overlay once those creds land.
const googleSignInEnabled = process.env.NEXT_PUBLIC_ENABLE_GOOGLE_SIGNIN === "true";

function SignInInner() {
  const router = useRouter();
  const params = useSearchParams();
  const next = sanitizeNext(params.get("next"));
  const { user, ready, signIn, signInWithEmail } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [googlePending, setGooglePending] = useState(false);
  const [emailPending, setEmailPending] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  useEffect(() => {
    if (ready && user) {
      router.replace(next);
    }
  }, [ready, user, next, router]);

  const onGoogleClick = useCallback(async () => {
    setError(null);
    setGooglePending(true);
    try {
      await signIn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Sign-in failed");
    } finally {
      setGooglePending(false);
    }
  }, [signIn]);

  const onEmailSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setError(null);
      setEmailPending(true);
      try {
        await signInWithEmail(email, password);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Sign-in failed");
      } finally {
        setEmailPending(false);
      }
    },
    [signInWithEmail, email, password],
  );

  return (
    <main className="mx-auto max-w-sm space-y-6 p-6">
      <h1 className="text-2xl font-bold">Sign in</h1>

      {googleSignInEnabled ? (
        <>
          <button
            type="button"
            onClick={onGoogleClick}
            disabled={googlePending || emailPending}
            className="w-full rounded-lg bg-white px-4 py-3 font-medium text-gray-800 shadow hover:shadow-md disabled:opacity-50"
          >
            {googlePending ? "Signing in…" : "Continue with Google"}
          </button>

          <div className="flex items-center gap-3">
            <hr className="flex-1 border-gray-600" />
            <span className="text-sm text-gray-400">or</span>
            <hr className="flex-1 border-gray-600" />
          </div>
        </>
      ) : null}

      <form onSubmit={onEmailSubmit} className="space-y-3">
        <label htmlFor="signin-email" className="sr-only">
          Email
        </label>
        <input
          id="signin-email"
          type="email"
          inputMode="email"
          placeholder="Email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
          className="w-full min-h-[44px] rounded-lg border border-gray-600 bg-transparent px-4 py-2"
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
          className="w-full min-h-[44px] rounded-lg border border-gray-600 bg-transparent px-4 py-2"
          data-testid="password-input"
        />
        <button
          type="submit"
          disabled={emailPending || googlePending}
          className="w-full min-h-[44px] rounded-lg bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {emailPending ? "Signing in…" : "Sign in with Email"}
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
