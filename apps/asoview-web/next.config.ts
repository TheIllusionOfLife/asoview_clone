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
function buildCsp(): string {
  const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const emulator = process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_URL;
  // Firebase Auth's `signInWithPopup` flow loads the auth domain in an
  // iframe (frame-src) and posts messages back to identitytoolkit
  // (connect-src). Both must be allowed in the CSP or the popup is
  // silently blocked. The carve-out is gated on the env var so the
  // production CSP only opens up the actual deployed auth domain.
  const authDomain = process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN;
  const authDomainOrigin =
    authDomain && authDomain !== "localhost" ? `https://${authDomain}` : null;
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
    "script-src 'self' https://js.stripe.com",
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
