"use client";

import { ApiError, NetworkError, SignInRedirect, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { OrderResponse, OrderStatus } from "@/lib/types";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

const PAGE_SIZE = 10;

const STATUS_LABELS: Record<OrderStatus, { label: string; tone: string }> = {
  PENDING: { label: "未決済", tone: "bg-[var(--color-bg)] text-[var(--color-ink-muted)]" },
  PAYMENT_PENDING: { label: "決済中", tone: "bg-yellow-100 text-yellow-800" },
  CONFIRMING: { label: "確認中", tone: "bg-yellow-100 text-yellow-800" },
  PAID: { label: "予約済み", tone: "bg-green-100 text-green-800" },
  CANCELLED: { label: "キャンセル", tone: "bg-[var(--color-bg)] text-[var(--color-ink-muted)]" },
  FAILED: { label: "失敗", tone: "bg-red-100 text-red-800" },
  REFUNDED: { label: "返金済", tone: "bg-[var(--color-bg)] text-[var(--color-ink-muted)]" },
};

function StatusBadge({ status }: { status: OrderStatus }) {
  const meta = STATUS_LABELS[status] ?? { label: status, tone: "bg-[var(--color-bg)]" };
  return (
    <span
      className={`inline-block rounded-[var(--radius-sm)] px-2 py-0.5 text-xs font-semibold ${meta.tone}`}
    >
      {meta.label}
    </span>
  );
}

function linkForOrder(o: OrderResponse): string | null {
  if (o.status === "PAID") return `/tickets/${o.orderId}`;
  if (o.status === "PAYMENT_PENDING" || o.status === "CONFIRMING") return `/checkout/${o.orderId}`;
  return null;
}

export function MyOrdersClient() {
  const router = useRouter();
  const search = useSearchParams();
  const page = Math.max(0, Number.parseInt(search.get("page") ?? "0", 10) || 0);
  const { ready, user } = useAuth();
  const [orders, setOrders] = useState<OrderResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      router.push("/signin?next=/me/orders");
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const list = await api.get<OrderResponse[]>("/v1/me/orders", {
          signal: ctrl.signal,
          currentPath: "/me/orders",
        });
        if (!cancelled) setOrders(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        setError(
          e instanceof ApiError || e instanceof NetworkError
            ? e.message
            : "予約履歴の取得中にエラーが発生しました",
        );
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, router]);

  const pageSlice = useMemo(() => {
    if (!orders) return [];
    return orders.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  }, [orders, page]);

  const totalPages = orders ? Math.max(1, Math.ceil(orders.length / PAGE_SIZE)) : 1;

  if (!ready || orders === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>;
  }
  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (orders.length === 0) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">まだ予約はありません。</p>;
  }
  return (
    <>
      <ul className="mt-6 space-y-3">
        {pageSlice.map((o) => {
          const href = linkForOrder(o);
          const body = (
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-mono text-xs text-[var(--color-ink-muted)]">{o.orderId}</p>
                <p className="mt-1 text-sm">
                  {o.items.length} 件 — {o.totalAmount} {o.currency}
                </p>
              </div>
              <StatusBadge status={o.status} />
            </div>
          );
          return (
            <li
              key={o.orderId}
              className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-sm)]"
            >
              {href ? (
                <Link href={href} className="block hover:opacity-90">
                  {body}
                </Link>
              ) : (
                body
              )}
            </li>
          );
        })}
      </ul>
      {totalPages > 1 && (
        <nav className="mt-6 flex items-center justify-center gap-3 text-sm">
          {page > 0 && (
            <Link
              href={`/me/orders?page=${page - 1}`}
              className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 hover:border-[var(--color-primary)]"
            >
              前へ
            </Link>
          )}
          <span className="text-[var(--color-ink-muted)]">
            {page + 1} / {totalPages}
          </span>
          {page + 1 < totalPages && (
            <Link
              href={`/me/orders?page=${page + 1}`}
              className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 hover:border-[var(--color-primary)]"
            >
              次へ
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
