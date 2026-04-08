"use client";

import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

/**
 * Header theme cycler: light -> dark -> system -> light. Guards against
 * SSR hydration mismatch by rendering a placeholder until mounted.
 *
 * The Header is rendered inside `[locale]/layout.tsx` so the
 * NextIntlClientProvider context IS available here; we use the standard
 * `useTranslations("theme")` hook.
 */
export function ThemeToggle() {
  const t = useTranslations("theme");
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

  const next = theme === "light" ? "dark" : theme === "dark" ? "system" : "light";
  const label = theme === "light" ? t("light") : theme === "dark" ? t("dark") : t("system");

  return (
    <button
      type="button"
      onClick={() => setTheme(next)}
      aria-label={t("toggleLabel")}
      title={t("toggleLabel")}
      className="inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-[var(--color-border)] px-2 py-1 text-xs hover:border-[var(--color-primary)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-[var(--color-primary)]"
    >
      <span aria-hidden="true">
        {theme === "dark" ? "\u263E" : theme === "light" ? "\u2600" : "\u2699"}
      </span>
      <span>{label}</span>
    </button>
  );
}
