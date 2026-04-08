"use client";

import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

/**
 * Header theme cycler: light -> dark -> system -> light. Guards against
 * SSR hydration mismatch by rendering a placeholder until mounted, since
 * the resolved theme is only known on the client.
 *
 * Lives in the root layout's <Header>, which is rendered OUTSIDE the
 * locale-scoped NextIntlClientProvider. We therefore inline the bilingual
 * labels via the `<html lang>` attribute rather than going through
 * `useTranslations`, which would crash during static prerender of any
 * route under [locale]/ that does not pass through that provider for the
 * Header subtree.
 */
const LABELS = {
  ja: { light: "ライト", dark: "ダーク", system: "システム", toggle: "テーマを切り替え" },
  en: { light: "Light", dark: "Dark", system: "System", toggle: "Toggle theme" },
} as const;

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) {
    return (
      <button
        type="button"
        aria-hidden="true"
        tabIndex={-1}
        className="w-9 h-9 rounded-[var(--radius-md)] border border-[var(--color-border)] opacity-0"
      />
    );
  }

  const lang = (typeof document !== "undefined" && document.documentElement.lang) || "ja";
  const L = lang.startsWith("en") ? LABELS.en : LABELS.ja;

  const next = theme === "light" ? "dark" : theme === "dark" ? "system" : "light";
  const label = theme === "light" ? L.light : theme === "dark" ? L.dark : L.system;

  return (
    <button
      type="button"
      onClick={() => setTheme(next)}
      aria-label={L.toggle}
      title={L.toggle}
      className="inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-[var(--color-border)] px-2 py-1 text-xs hover:border-[var(--color-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--color-primary)]"
    >
      <span aria-hidden="true">
        {theme === "dark" ? "\u263E" : theme === "light" ? "\u2600" : "\u2699"}
      </span>
      <span>{label}</span>
    </button>
  );
}
