"use client";

/**
 * Multi-line cart page. Edits quantity, removes lines, displays subtotal,
 * and (commit 11) submits a single multi-item POST /v1/orders. Per-line
 * 409 errors surface inline so the user can swap a single failing slot
 * without losing the rest of the cart.
 */

import { useAuth } from "@/lib/auth";
import { useCart } from "@/lib/useCart";
import Link from "next/link";

function formatJpy(amount: number): string {
  return new Intl.NumberFormat("ja-JP", {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 0,
  }).format(Math.trunc(amount));
}

function formatSlotWindow(start: string, end: string): string {
  // ISO datetime → "YYYY/MM/DD HH:mm–HH:mm"
  const s = new Date(start);
  const e = new Date(end);
  if (Number.isNaN(s.getTime()) || Number.isNaN(e.getTime())) return `${start} – ${end}`;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${s.getFullYear()}/${pad(s.getMonth() + 1)}/${pad(s.getDate())} ${pad(
    s.getHours(),
  )}:${pad(s.getMinutes())}–${pad(e.getHours())}:${pad(e.getMinutes())}`;
}

export default function CartPage() {
  const { cart, hydrated, subtotal, setQty, remove } = useCart();
  const { user, ready } = useAuth();

  if (!hydrated || !ready) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="font-display text-3xl font-bold">カート</h1>
        <p className="mt-4 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>
      </div>
    );
  }

  if (cart.lines.length === 0) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="font-display text-3xl font-bold">カート</h1>
        <p className="mt-6 text-sm text-[var(--color-ink-muted)]">カートには何も入っていません。</p>
        <Link
          href="/"
          className="mt-6 inline-block rounded-[var(--radius-md)] border border-[var(--color-border)] px-4 py-2 text-sm hover:border-[var(--color-primary)]"
        >
          体験を探す
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">カート</h1>

      <ul className="mt-6 space-y-3">
        {cart.lines.map((l) => (
          <li
            key={l.slotId}
            className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-sm)]"
          >
            <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3">
              <div className="min-w-0">
                <Link
                  href={`/products/${l.productId}`}
                  className="font-display text-lg font-semibold hover:text-[var(--color-primary)]"
                >
                  {l.productSnapshot.name}
                </Link>
                {l.productSnapshot.area && (
                  <p className="text-xs text-[var(--color-ink-muted)]">{l.productSnapshot.area}</p>
                )}
                <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
                  {formatSlotWindow(l.slotStartAt, l.slotEndAt)}
                </p>
                <p className="mt-1 text-sm font-semibold text-[var(--color-primary)]">
                  {formatJpy(Number(l.unitPrice))} × {l.quantity}名
                </p>
              </div>
              <div className="flex items-center gap-2">
                <label className="text-sm">
                  <span className="sr-only">人数を変更</span>
                  <select
                    value={l.quantity}
                    onChange={(e) => setQty(l.slotId, Number(e.target.value))}
                    aria-label={`${l.productSnapshot.name} の人数`}
                    className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-1.5 text-sm focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]"
                  >
                    {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>
                        {n}名
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  onClick={() => remove(l.slotId)}
                  aria-label={`${l.productSnapshot.name} をカートから削除`}
                  className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 text-sm hover:border-[var(--color-danger)] hover:text-[var(--color-danger)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]"
                >
                  削除
                </button>
              </div>
            </div>
          </li>
        ))}
      </ul>

      <div className="mt-6 flex items-center justify-between rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <span className="text-sm text-[var(--color-ink-muted)]">合計</span>
        <span className="text-xl font-semibold text-[var(--color-primary)]">
          {formatJpy(subtotal)}
        </span>
      </div>

      <div className="mt-4 text-right">
        {user ? (
          <button
            type="button"
            disabled
            aria-disabled
            className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white opacity-60"
          >
            購入手続きへ
          </button>
        ) : (
          <Link
            href="/signin?next=/cart"
            className="inline-block rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white hover:shadow-[var(--shadow-md)]"
          >
            ログインして購入手続き
          </Link>
        )}
      </div>
    </div>
  );
}
