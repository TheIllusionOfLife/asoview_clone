import type { NextConfig } from "next";

/**
 * Build a CSP string from the active env. The connect-src list reads
 * NEXT_PUBLIC_API_BASE_URL at build time so the prod CSP only allows
 * the gateway origin we actually deploy against. The Firebase Auth
 * Emulator origin is included only when NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL
 * is set (local + CI), keeping the production CSP tight.
 *
 * Tailwind v4 carve-out: 'unsafe-inline' is required on `style-src`
 * because Tailwind v4 inlines critical CSS at build time. This is the
 * documented Tailwind v4 posture; revisit if Tailwind ships nonced
 * inline styles.
 */
/**
 * Parse a raw env string into a valid CSP source (URL origin only). Returns
 * null for missing/blank/unparseable values so callers can decide whether to
 * fail or skip. This avoids injecting path components or malformed strings
 * into the CSP, which browsers either reject or silently truncate.
 */
function toOrigin(raw: string | undefined | null): string | null {
  if (!raw) return null;
  try {
    return new URL(raw).origin;
  } catch {
    return null;
  }
}

function buildCsp(): string {
  const rawApiBase = process.env.NEXT_PUBLIC_API_BASE_URL;
  const apiBase = toOrigin(rawApiBase) ?? toOrigin("http://localhost:8080");
  if (!apiBase) {
    throw new Error("Unable to derive API origin for CSP (unreachable fallback)");
  }
  // Fail fast in production if NEXT_PUBLIC_API_BASE_URL is missing or
  // unparseable — shipping with the localhost fallback would produce a
  // broken CSP that either blocks all API calls or, worse, allows
  // localhost in a deployed environment.
  if (process.env.NODE_ENV === "production" && !toOrigin(rawApiBase)) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL must be set to a valid URL in production builds");
  }
  const emulator = toOrigin(process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL);
  // Firebase Auth's `signInWithPopup` flow loads the auth domain in an
  // iframe (frame-src) and posts messages back to identitytoolkit
  // (connect-src). Both must be allowed in the CSP or the popup is
  // silently blocked. The carve-out is gated on the env var so the
  // production CSP only opens up the actual deployed auth domain.
  const authDomain = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN;
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
  return [
    "default-src 'self'",
    // Next.js 16 App Router emits inline hydration scripts (RSC stream
    // bootstrap, __next_f bridge). Without 'unsafe-inline' the browser
    // blocks hydration and every client component stays on its SSR
    // skeleton forever. A nonce-based middleware is the proper
    // hardened approach and should land in a follow-up PR; for now
    // we accept the same posture as 'style-src 'unsafe-inline' above.
    "script-src 'self' 'unsafe-inline' https://js.stripe.com",
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

const securityHeaders = [
  { key: "Content-Security-Policy", value: buildCsp() },
  {
    key: "Strict-Transport-Security",
    value: "max-age=63072000; includeSubDomains; preload",
  },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  {
    key: "Permissions-Policy",
    value: "camera=(), microphone=(), geolocation=()",
  },
];

const nextConfig: NextConfig = {
  output: "standalone",
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
    ];
  },
};

export default nextConfig;
