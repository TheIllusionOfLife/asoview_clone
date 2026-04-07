"use client";

import { getPointsBalance } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useEffect, useState } from "react";

/**
 * Header pill showing the current points balance. Renders nothing when
 * signed out. Backend endpoint: GET /v1/me/points -> {balance}.
 */
export function PointsBalance() {
  const { ready, user } = useAuth();
  const [balance, setBalance] = useState<number | null>(null);

  useEffect(() => {
    if (!ready || !user) {
      setBalance(null);
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const { balance: b } = await getPointsBalance({ signal: ctrl.signal });
        if (!cancelled) setBalance(b);
      } catch {
        if (!cancelled) setBalance(null);
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user]);

  if (!user || balance === null) return null;
  return (
    <span
      aria-label="保有ポイント"
      className="inline-flex items-center gap-1 rounded-full bg-[var(--color-bg)] px-2 py-0.5 text-xs text-[var(--color-ink-muted)]"
    >
      {balance} pt
    </span>
  );
}
