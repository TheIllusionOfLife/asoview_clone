"use client";

import { useTranslations } from "next-intl";
import { Link, usePathname } from "@/i18n/navigation";
import { AuthProvider, useAuth } from "@/lib/auth";
import { type ReactNode, useState } from "react";

function Sidebar() {
  const t = useTranslations("app");
  const pathname = usePathname();
  const { signOut } = useAuth();
  const [open, setOpen] = useState(false);

  const links = [
    { href: "/", label: t("dashboard") },
    { href: "/slots", label: t("slots") },
    { href: "/reservations", label: t("reservations") },
  ];

  return (
    <>
      <button
        type="button"
        className="md:hidden fixed top-4 left-4 z-50 p-2 rounded-md bg-white shadow-md"
        onClick={() => setOpen(!open)}
      >
        <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
        </svg>
      </button>
      <aside className={`fixed inset-y-0 left-0 z-40 w-64 bg-white border-r border-[var(--color-border)] transform transition-transform md:translate-x-0 ${open ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="p-6">
          <h1 className="text-lg font-bold text-[var(--color-primary)]">{t("title")}</h1>
        </div>
        <nav className="px-4 space-y-1">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={`block px-4 py-2 rounded-[var(--radius-md)] text-sm transition-colors ${
                pathname === link.href
                  ? "bg-[var(--color-primary)] text-white"
                  : "text-[var(--color-text-muted)] hover:bg-[var(--color-surface-alt)]"
              }`}
              onClick={() => setOpen(false)}
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="absolute bottom-0 w-full p-4 border-t border-[var(--color-border)]">
          <button
            type="button"
            onClick={() => signOut()}
            className="w-full px-4 py-2 text-sm text-[var(--color-text-muted)] hover:text-[var(--color-danger)] transition-colors text-left"
          >
            {t("logout")}
          </button>
        </div>
      </aside>
      {open && (
        <div
          className="fixed inset-0 z-30 bg-black/20 md:hidden"
          onClick={() => setOpen(false)}
          onKeyDown={() => {}}
          role="presentation"
        />
      )}
    </>
  );
}

function AuthGate({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  const pathname = usePathname();

  if (!ready) {
    return (
      <html lang="ja">
        <body className="flex items-center justify-center min-h-screen">
          <p className="text-[var(--color-text-muted)]">Loading...</p>
        </body>
      </html>
    );
  }

  if (!user && pathname !== "/login") {
    return (
      <html lang="ja">
        <body>
          <meta httpEquiv="refresh" content={`0;url=/ja/login`} />
        </body>
      </html>
    );
  }

  if (pathname === "/login") {
    return (
      <html lang="ja">
        <body>{children}</body>
      </html>
    );
  }

  return (
    <html lang="ja">
      <body>
        <Sidebar />
        <main className="md:ml-64 p-6 min-h-screen">{children}</main>
      </body>
    </html>
  );
}

export default function LocaleLayout({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AuthGate>{children}</AuthGate>
    </AuthProvider>
  );
}
