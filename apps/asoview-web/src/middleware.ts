/**
 * next-intl middleware: negotiates the active locale and rewrites requests
 * to the `[locale]` segment. Anything matched here gets a `/ja` or `/en`
 * prefix; anything excluded by the matcher below is left alone so the
 * healthz probe, Next internals, and static assets don't get an unwanted
 * locale rewrite.
 */
import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

export default createMiddleware(routing);

export const config = {
  // Match all paths EXCEPT:
  //  - /_next/*         (Next internals, static chunks)
  //  - /api/*           (any future API routes we add)
  //  - /healthz         (liveness probe consumed by k8s; must stay un-prefixed)
  //  - anything with a dot in the last segment (e.g. /favicon.ico, /robots.txt)
  matcher: ["/((?!api|_next|healthz|.*\\..*).*)"],
};
