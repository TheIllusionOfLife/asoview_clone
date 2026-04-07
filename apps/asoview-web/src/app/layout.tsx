/**
 * Root layout. Next.js requires `<html>` + `<body>` in the root layout,
 * so `lang` is set to the default locale here and overridden at runtime
 * via `<html lang>` hydration mismatch only when necessary. The
 * next-intl provider + messages wiring lives under `[locale]/layout.tsx`
 * so it has access to the active route segment.
 */
import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";
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
    <html lang="ja" className={`${fraunces.variable} ${notoSansJp.variable}`}>
      <body className="min-h-screen flex flex-col">
        <AuthProvider>
          <Header />
          <main className="flex-1">{children}</main>
          <Footer />
        </AuthProvider>
      </body>
    </html>
  );
}
