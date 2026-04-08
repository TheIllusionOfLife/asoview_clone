import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";
import { buildCsp } from "./src/lib/csp";

// Wire next-intl's request-scoped config loader. Passing an explicit path
// avoids relying on the default `./i18n/request.ts` convention so we can
// keep all i18n plumbing under src/i18n/.
const withNextIntl = createNextIntlPlugin("./src/i18n/config.ts");

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

// Cast: the monorepo hoists `next` twice, so withNextIntl's NextConfig
// type comes from a different copy than the one our local import sees.
// The runtime shape is identical.
export default withNextIntl(nextConfig as Parameters<typeof withNextIntl>[0]);
