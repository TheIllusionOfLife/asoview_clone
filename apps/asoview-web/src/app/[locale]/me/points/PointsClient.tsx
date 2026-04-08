"use client";

import { useRouter } from "@/i18n/navigation";
import { ApiError, NetworkError, SignInRedirect, getPointsBalance } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

/**
 * Shows the current points balance. The backend does not yet expose a
 * ledger endpoint (GET /v1/me/points/ledger is not implemented), so the
 * ledger section is a placeholder until PR #21 lands the read side.
 */
export function PointsClient() {
  const t = useTranslations("points");
  const router = useRouter();
  const { ready, user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      router.push("/signin?next=/me/points");
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const { balance: b } = await getPointsBalance({
          signal: ctrl.signal,
          currentPath: "/me/points",
        });
        if (!cancelled) setBalance(b);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        setError(e instanceof ApiError || e instanceof NetworkError ? e.message : t("loadError"));
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, router, t]);

  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (!ready || balance === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>;
  }
  return (
    <div className="mt-6 space-y-6">
      <div className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6">
        <p className="text-sm text-[var(--color-ink-muted)]">{t("balance")}</p>
        <p className="mt-2 text-3xl font-bold text-[var(--color-primary)]">{balance} pt</p>
      </div>
      <section>
        <h2 className="font-display text-xl font-semibold">{t("ledgerTitle")}</h2>
        <p className="mt-2 text-sm text-[var(--color-ink-muted)]">{t("ledgerComingSoon")}</p>
      </section>
    </div>
  );
}
