/**
 * Shared routing configuration for next-intl. Consumed by:
 *   - src/middleware.ts (locale negotiation + prefix rewriting)
 *   - src/i18n/navigation.ts (locale-aware Link/useRouter wrappers)
 *   - src/i18n/config.ts (request-scoped messages loader)
 *
 * Keep locales and defaultLocale in ONE place so adding a third locale
 * is a one-line change.
 */
import { defineRouting } from "next-intl/routing";

export const routing = defineRouting({
  locales: ["ja", "en"] as const,
  defaultLocale: "ja",
  localePrefix: "always",
});

export type Locale = (typeof routing.locales)[number];
