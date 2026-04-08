"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { ReactNode } from "react";

/**
 * Wraps next-themes' provider with the project defaults: writes the
 * theme as a `class` on `<html>` so the `.dark` selector in globals.css
 * (and the `dark:` Tailwind variant) take effect, and falls back to the
 * OS preference when the user has not made an explicit choice.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      {children}
    </NextThemesProvider>
  );
}
