import { hasLocale } from "next-intl";
/**
 * next-intl configuration for the App Router.
 *
 * - Two locales: `ja` (default) and `en`.
 * - `localePrefix: "always"` so every request path carries `/ja/...` or
 *   `/en/...`. This is what lets the middleware + the `[locale]` segment
 *   agree without magic fallbacks.
 * - `getRequestConfig` loads the per-locale messages JSON at request time
 *   and is wired via `next-intl/plugin` in `next.config.ts` (see
 *   `createNextIntlPlugin`).
 *
 * Keep this file server-only — the request-config callback runs on the
 * Node side of SSR and must not be imported from a client component.
 */
import { getRequestConfig } from "next-intl/server";
import { routing } from "./routing";

export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = hasLocale(routing.locales, requested) ? requested : routing.defaultLocale;
  return {
    locale,
    messages: (await import(`../../messages/${locale}.json`)).default,
  };
});
