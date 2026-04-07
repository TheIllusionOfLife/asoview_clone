"use client";

import { useRouter } from "@/i18n/navigation";
import { ApiError, NetworkError, SignInRedirect, getPointsBalance } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useEffect, useState } from "react";

/**
 * Shows the current points balance. The backend does not yet expose a
 * ledger endpoint (GET /v1/me/points/ledger is not implemented), so the
 * ledger section is a placeholder until PR #21 lands the read side.
 */
export function PointsClient() {
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
        setError(
          e instanceof ApiError || e instanceof NetworkError
            ? e.message
            : "ポイントの取得に失敗しました",
        );
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, router]);

  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (!ready || balance === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>;
  }
  return (
    <div className="mt-6 space-y-6">
      <div className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6">
        <p className="text-sm text-[var(--color-ink-muted)]">保有ポイント</p>
        <p className="mt-2 text-3xl font-bold text-[var(--color-primary)]">{balance} pt</p>
      </div>
      <section>
        <h2 className="font-display text-xl font-semibold">履歴</h2>
        <p className="mt-2 text-sm text-[var(--color-ink-muted)]">
          ポイント履歴は近日公開予定です。
        </p>
      </section>
    </div>
  );
}
