"use client";

import { useTranslations } from "next-intl";
import { type ReactNode, useEffect } from "react";
import { usePathname, useRouter } from "@/i18n/navigation";
import { AuthProvider, useAuth } from "@/lib/auth";

function AuthContent({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const tc = useTranslations("common");

  useEffect(() => {
    if (ready && !user && pathname !== "/login") {
      router.replace("/login");
    }
  }, [ready, user, pathname, router]);

  if (!ready) {
    return (
      <p className="flex items-center justify-center min-h-screen text-[var(--color-text-muted)]">
        {tc("loading")}
      </p>
    );
  }

  if (!user && pathname !== "/login") {
    return null;
  }

  return <main className="min-h-screen">{children}</main>;
}

export function AuthGate({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <AuthContent>{children}</AuthContent>
    </AuthProvider>
  );
}
