/**
 * Build the Content-Security-Policy header value from environment.
 *
 * Lives under src/ (not next.config.ts) so Vitest can import and assert
 * against it directly. next.config.ts re-exports the same function.
 *
 * Production posture is tight: no 'unsafe-eval', explicit allowlists for
 * Stripe + Firebase Auth. Development adds the React dev-build runtime
 * code-string token to script-src so the React error overlay does not
 * permanently mount on every page.
 */

function toOrigin(raw: string | undefined | null): string | null {
  if (!raw) return null;
  try {
    return new URL(raw).origin;
  } catch {
    return null;
  }
}

export function buildCsp(env: NodeJS.ProcessEnv = process.env): string {
  const rawApiBase = env.NEXT_PUBLIC_API_BASE_URL;
  const apiBase = toOrigin(rawApiBase) ?? toOrigin("http://localhost:8080");
  if (!apiBase) {
    throw new Error("Unable to derive API origin for CSP (unreachable fallback)");
  }
  if (env.NODE_ENV === "production" && !toOrigin(rawApiBase)) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must be set to a valid URL in production builds");
  }
  const emulator = toOrigin(env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL);
  const authDomain = env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN;
  const authDomainOrigin =
    authDomain && authDomain !== "localhost" ? toOrigin(`https://${authDomain}`) : null;
  const connect = [
    "'self'",
    apiBase,
    "https://api.stripe.com",
    "https://identitytoolkit.googleapis.com",
    "https://securetoken.googleapis.com",
  ];
  if (emulator) connect.push(emulator);
  if (authDomainOrigin) connect.push(authDomainOrigin);
  const frame = ["'self'", "https://js.stripe.com", "https://hooks.stripe.com"];
  if (authDomainOrigin) frame.push(authDomainOrigin);
  // React dev needs runtime code-string compilation for stack-trace
  // reconstruction; production never does. Token gated on NODE_ENV so the
  // deployed CSP keeps its tight posture.
  const scriptSrc = [
    "'self'",
    "'unsafe-inline'",
    "https://js.stripe.com",
    ...(env.NODE_ENV === "production" ? [] : ["'unsafe-eval'"]),
  ].join(" ");
  return [
    "default-src 'self'",
    `script-src ${scriptSrc}`,
    "style-src 'self' 'unsafe-inline'",
    "font-src 'self' data:",
    "img-src 'self' data: https://*.googleusercontent.com",
    `connect-src ${connect.join(" ")}`,
    `frame-src ${frame.join(" ")}`,
    "frame-ancestors 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "object-src 'none'",
  ].join("; ");
}
