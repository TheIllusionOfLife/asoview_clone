"use client";

import { Link, useRouter } from "@/i18n/navigation";
import {
  ApiError,
  NetworkError,
  type PointLedgerEntry,
  type PointLedgerPage,
  SignInRedirect,
  getPointsBalance,
  getPointsLedger,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";

function formatJstDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("ja-JP", { timeZone: "Asia/Tokyo", dateStyle: "medium" });
}

function DirectionBadge({ direction }: { direction: PointLedgerEntry["direction"] }) {
  const colors: Record<string, string> = {
    EARN: "bg-green-100 text-green-800",
    BURN: "bg-red-100 text-red-800",
    REFUND: "bg-blue-100 text-blue-800",
  };
  const signs: Record<string, string> = { EARN: "+", BURN: "-", REFUND: "+" };
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-semibold ${colors[direction]}`}>
      {signs[direction]} {direction}
    </span>
  );
}

export function PointsClient() {
  const t = useTranslations("points");
  const router = useRouter();
  const { ready, user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);
  const [ledger, setLedger] = useState<PointLedgerPage | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [ledgerError, setLedgerError] = useState<string | null>(null);

  const handleError = useCallback(
    (e: unknown, setter: (msg: string) => void = setError) => {
      if (e instanceof SignInRedirect) {
        router.push(`/signin?next=${encodeURIComponent(e.next)}`);
        return;
      }
      setter(e instanceof ApiError || e instanceof NetworkError ? e.message : t("loadError"));
    },
    [router, t],
  );

  // Fetch balance on mount and when auth/locale state changes (not on page navigation).
  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        setError(null);
        const res = await getPointsBalance({ signal: ctrl.signal, currentPath: "/me/points" });
        if (!cancelled) setBalance(res.balance);
      } catch (e) {
        if (!cancelled) handleError(e);
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, handleError]);

  // Fetch ledger on mount and when page changes.
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
        setLedgerError(null);
        const res = await getPointsLedger(page, 20, {
          signal: ctrl.signal,
          currentPath: "/me/points",
        });
        if (!cancelled) setLedger(res);
      } catch (e) {
        if (cancelled) return;
        handleError(e, setLedgerError);
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, router, page, handleError]);

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
        <p className="mt-2 text-3xl font-bold text-[var(--color-primary)]">
          {t("balanceValue", { balance })}
        </p>
      </div>
      <section data-testid="points-ledger">
        <h2 className="font-display text-xl font-semibold">{t("ledgerTitle")}</h2>
        {ledgerError && (
          <p role="alert" className="mt-2 text-sm text-[var(--color-danger)]">
            {ledgerError}
          </p>
        )}
        {!ledgerError && ledger && ledger.content.length === 0 && (
          <p className="mt-2 text-sm text-[var(--color-ink-muted)]">{t("ledgerEmpty")}</p>
        )}
        {!ledgerError && ledger && ledger.content.length > 0 && (
          <>
            <ul className="mt-4 divide-y divide-[var(--color-border)]">
              {ledger.content.map((entry) => (
                <li key={entry.id} className="flex items-center gap-3 py-3">
                  <DirectionBadge direction={entry.direction} />
                  <span className="font-semibold tabular-nums">
                    {entry.direction === "BURN" ? "-" : "+"}
                    {entry.amount.toLocaleString()}
                  </span>
                  <span className="flex-1 text-sm text-[var(--color-ink-muted)]">
                    {entry.reason}
                  </span>
                  {entry.referenceId && (
                    <Link
                      href="/me/orders"
                      className="text-xs text-[var(--color-primary)] underline"
                    >
                      {t("viewOrder")}
                    </Link>
                  )}
                  <span className="text-xs text-[var(--color-ink-muted)]">
                    {formatJstDate(entry.createdAt)}
                  </span>
                </li>
              ))}
            </ul>
            <div className="mt-4 flex items-center gap-4">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="rounded border px-3 py-1 text-sm disabled:opacity-40"
              >
                {t("prev")}
              </button>
              <span className="text-sm text-[var(--color-ink-muted)]">
                {page + 1} / {ledger.totalPages}
              </span>
              <button
                type="button"
                disabled={page >= ledger.totalPages - 1}
                onClick={() => setPage((p) => p + 1)}
                className="rounded border px-3 py-1 text-sm disabled:opacity-40"
              >
                {t("next")}
              </button>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
