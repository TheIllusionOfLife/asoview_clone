/**
 * Root layout. Next.js requires `<html>` + `<body>` in the root layout,
 * so `lang` is set to the default locale here and overridden at runtime
 * via `<html lang>` hydration mismatch only when necessary. Header and
 * Footer are rendered inside `[locale]/layout.tsx` so they have access
 * to the `NextIntlClientProvider` (next-intl `Link`/`useTranslations`
 * throw without an intl context).
 */
import { ThemeProvider } from "@/components/ThemeProvider";
import { AuthProvider } from "@/lib/auth";
import { Fraunces, Noto_Sans_JP } from "next/font/google";
import "./globals.css";

const fraunces = Fraunces({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-fraunces",
  weight: ["400", "600", "700"],
});

const notoSansJp = Noto_Sans_JP({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-noto-sans-jp",
  weight: ["400", "500", "700"],
});

export const metadata = {
  title: "Asoview",
  description: "日本のレジャー・体験予約",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="ja"
      suppressHydrationWarning
      className={`${fraunces.variable} ${notoSansJp.variable}`}
    >
      <body className="min-h-screen flex flex-col">
        <ThemeProvider>
          <AuthProvider>{children}</AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
