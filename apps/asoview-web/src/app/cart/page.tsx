"use client";

/**
 * Multi-line cart page. Edits quantity, removes lines, displays subtotal,
 * and (commit 11) submits a single multi-item POST /v1/orders. Per-line
 * 409 errors surface inline so the user can swap a single failing slot
 * without losing the rest of the cart.
 */

import { ApiError, NetworkError, SignInRedirect, SlotTakenError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { clearIdempotencyKey, getOrCreateIdempotencyKey } from "@/lib/idempotency";
import type { CreateOrderRequest, OrderResponse } from "@/lib/types";
import { useCart } from "@/lib/useCart";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useState } from "react";

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

/**
 * Synthesise an Idempotency-Key fingerprint for a multi-line cart by
 * concatenating the (slotId, quantity) pairs in sorted order. Same cart
 * → same key, so a refresh mid-checkout does not double-book the cart.
 * Different cart contents → different key.
 */
function cartFingerprint(lines: { slotId: string; quantity: number }[]) {
  const key = [...lines]
    .sort((a, b) => a.slotId.localeCompare(b.slotId))
    .map((l) => `${l.slotId}:${l.quantity}`)
    .join(",");
  return { productId: "cart", slotId: key, quantity: lines.length };
}

export default function CartPage() {
  const router = useRouter();
  const { cart, hydrated, subtotal, setQty, remove } = useCart();
  const { user, ready } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [lineErrors, setLineErrors] = useState<Record<string, string>>({});

  const onCheckout = useCallback(async () => {
    if (cart.lines.length === 0) return;
    setGeneralError(null);
    setLineErrors({});
    if (!user) {
      router.push("/signin?next=/cart");
      return;
    }
    const fp = cartFingerprint(cart.lines);
    const body: CreateOrderRequest = {
      items: cart.lines.map((l) => ({
        productVariantId: l.productVariantId,
        slotId: l.slotId,
        quantity: l.quantity,
      })),
    };
    setSubmitting(true);
    try {
      const order = await api.post<OrderResponse>("/v1/orders", body, { idempotency: fp });
      // Cart is "consumed" — reset key for the next checkout attempt and
      // route to the per-order checkout page. We intentionally do NOT
      // clear the cart locally yet: the order can still fail at payment
      // time (Session D) and we want the lines to remain for a retry
      // until the order reaches a terminal state.
      clearIdempotencyKey(fp);
      router.push(`/checkout/${order.orderId}`);
    } catch (e: unknown) {
      setSubmitting(false);
      if (e instanceof SignInRedirect) {
        router.push(`/signin?next=${encodeURIComponent(e.next)}`);
        return;
      }
      if (e instanceof SlotTakenError) {
        // The backend's 409 message format is opaque from the contract;
        // try to extract a slotId substring so we can pin the error to
        // the right line. Fallback: blanket banner above the cart.
        clearIdempotencyKey(fp);
        const matched = cart.lines.find((l) => e.message.includes(l.slotId));
        if (matched) {
          setLineErrors({
            [matched.slotId]: "この時間帯は満席になりました。別の時間帯を選んでください。",
          });
        } else {
          setGeneralError(
            "カート内の一部の時間帯が満席になりました。該当する枠を選び直してください。",
          );
        }
        return;
      }
      setGeneralError(
        e instanceof ApiError || e instanceof NetworkError
          ? e.message
          : "予約処理中にエラーが発生しました",
      );
    }
  }, [cart.lines, router, user]);

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

      {generalError && (
        <p
          role="alert"
          className="mt-4 rounded-[var(--radius-md)] border border-[var(--color-danger)] bg-[var(--color-danger)]/10 p-3 text-sm text-[var(--color-danger)]"
        >
          {generalError}
        </p>
      )}

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
            {lineErrors[l.slotId] && (
              <p
                role="alert"
                className="mt-2 rounded-[var(--radius-sm)] bg-[var(--color-danger)]/10 px-2 py-1 text-xs text-[var(--color-danger)]"
              >
                {lineErrors[l.slotId]}
              </p>
            )}
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
            onClick={onCheckout}
            disabled={submitting}
            className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)] disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting ? "処理中…" : "購入手続きへ"}
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
