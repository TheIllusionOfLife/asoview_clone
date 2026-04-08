"use client";

/**
 * Multi-line cart page. Edits quantity, removes lines, displays subtotal,
 * and (commit 11) submits a single multi-item POST /v1/orders. Per-line
 * 409 errors surface inline so the user can swap a single failing slot
 * without losing the rest of the cart.
 */

import { Link } from "@/i18n/navigation";
import { useRouter } from "@/i18n/navigation";
import {
  ApiError,
  NetworkError,
  SignInRedirect,
  SlotTakenError,
  api,
  getPointsBalance,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { parseMinorUnits } from "@/lib/cart";
import { clearIdempotencyKey, setOrderFingerprint } from "@/lib/idempotency";
import type { CreateOrderRequest, OrderResponse } from "@/lib/types";
import { useCart } from "@/lib/useCart";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";

function formatJpy(amount: number | string, locale: string): string {
  // Display-only formatter. Japanese retail is integer yen, so Math.trunc
  // is the intentional rounding mode. NUMERIC money strings (e.g. "1500.00")
  // go through parseMinorUnits (integer minor units) / 100 so no Number()
  // coercion crosses the money boundary (CLAUDE.md NUMERIC rule).
  const yen =
    typeof amount === "number" ? Math.trunc(amount) : Math.trunc(parseMinorUnits(amount) / 100);
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 0,
  }).format(Number.isFinite(yen) ? yen : 0);
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
  const t = useTranslations("cart");
  const tPoints = useTranslations("cart.points");
  const locale = useLocale();
  const router = useRouter();
  const { cart, hydrated, subtotal, setQty, remove } = useCart();
  const { user, ready } = useAuth();
  const [submitting, setSubmitting] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [lineErrors, setLineErrors] = useState<Record<string, string>>({});
  const [pointsBalance, setPointsBalance] = useState<number | null>(null);
  const [pointsToUse, setPointsToUse] = useState<number>(0);

  // Fetch the user's points balance once they are signed in. Fail silent —
  // points are optional and a fetch failure should not block checkout.
  useEffect(() => {
    if (!ready || !user) return;
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const { balance } = await getPointsBalance({ signal: ctrl.signal });
        if (!cancelled) setPointsBalance(balance);
      } catch {
        if (!cancelled) setPointsBalance(null);
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user]);

  // Clamp 0 <= pointsToUse <= min(balance, subtotal). Yen integer minor units.
  // NUMERIC string subtotal -> integer yen via parseMinorUnits (sen / 100).
  const subtotalYen = Math.trunc(parseMinorUnits(subtotal) / 100);
  const maxPoints = Math.max(
    0,
    Math.min(Number.isFinite(subtotalYen) ? subtotalYen : 0, pointsBalance ?? 0),
  );
  const clampedPoints = Math.max(0, Math.min(maxPoints, Math.trunc(pointsToUse || 0)));
  const totalAfterPoints = Math.max(0, subtotalYen - clampedPoints);

  // Keep the visible input in sync with the derived clamp: if maxPoints
  // shrinks (balance refetch, line removed) the user should not see a
  // stale higher value that conflicts with the submitted amount. Do NOT
  // depend on `pointsToUse` itself — that would loop.
  useEffect(() => {
    setPointsToUse((cur) => Math.max(0, Math.min(maxPoints, Math.trunc(cur || 0))));
  }, [maxPoints]);

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
      ...(clampedPoints > 0 ? { pointsToUse: clampedPoints } : {}),
    };
    setSubmitting(true);
    try {
      const order = await api.post<OrderResponse>("/v1/orders", body, { idempotency: fp });
      // Persist the (orderId → fingerprint) mapping so that CheckoutClient
      // can clear the Idempotency-Key only when the order reaches a
      // terminal state (PAID / CANCELLED / FAILED). Clearing here would
      // let a back-navigation mint a fresh key on the same draft and
      // create a duplicate order on the backend.
      setOrderFingerprint(order.orderId, fp);
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
            [matched.slotId]: t("slotTakenLine"),
          });
        } else {
          setGeneralError(t("slotTakenGeneral"));
        }
        return;
      }
      setGeneralError(
        e instanceof ApiError || e instanceof NetworkError ? e.message : t("checkoutError"),
      );
    }
  }, [cart.lines, router, user, clampedPoints, t]);

  if (!hydrated || !ready) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="font-display text-3xl font-bold">{t("title")}</h1>
        <p className="mt-4 text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>
      </div>
    );
  }

  if (cart.lines.length === 0) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10">
        <h1 className="font-display text-3xl font-bold">{t("title")}</h1>
        <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("empty")}</p>
        <Link
          href="/"
          className="mt-6 inline-block rounded-[var(--radius-md)] border border-[var(--color-border)] px-4 py-2 text-sm hover:border-[var(--color-primary)]"
        >
          {t("browseExperiences")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">{t("title")}</h1>

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
                  {formatJpy(l.unitPrice, locale)} {t("lineQuantity", { n: l.quantity })}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <label className="text-sm">
                  <span className="sr-only">{t("changeQuantityAria")}</span>
                  <select
                    value={l.quantity}
                    onChange={(e) =>
                      setQty(
                        l.slotId,
                        Number(e.target.value) /* money-parse-ok: quantity, not money */,
                      )
                    }
                    aria-label={t("quantityForAria", { name: l.productSnapshot.name })}
                    className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-2 py-1.5 text-sm focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]"
                  >
                    {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                      <option key={n} value={n}>
                        {t("peopleSuffix", { n })}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  onClick={() => remove(l.slotId)}
                  aria-label={t("removeAria", { name: l.productSnapshot.name })}
                  className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 text-sm hover:border-[var(--color-danger)] hover:text-[var(--color-danger)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]"
                >
                  {t("removeLabel")}
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

      {user && pointsBalance !== null && pointsBalance > 0 && (
        <div className="mt-6 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
          <label className="flex items-center justify-between gap-3 text-sm">
            <span>
              {tPoints("label")}
              <span className="ml-2 text-xs text-[var(--color-ink-muted)]">
                {tPoints("balanceHelp", { balance: pointsBalance, max: maxPoints })}
              </span>
            </span>
            <input
              type="number"
              min={0}
              max={maxPoints}
              step={1}
              value={pointsToUse}
              onChange={(e) => {
                const v = Number.parseInt(e.target.value, 10);
                if (Number.isNaN(v)) {
                  setPointsToUse(0);
                } else {
                  setPointsToUse(Math.max(0, Math.min(maxPoints, Math.trunc(v))));
                }
              }}
              aria-label={tPoints("inputLabel")}
              className="w-28 rounded-[var(--radius-sm)] border border-[var(--color-border)] px-2 py-1 text-right"
            />
          </label>
          <p className="mt-2 text-xs text-[var(--color-ink-muted)]">{tPoints("helper")}</p>
        </div>
      )}

      <div className="mt-6 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        {clampedPoints > 0 && (
          <div className="flex items-center justify-between text-sm text-[var(--color-ink-muted)]">
            <span>{t("pointsLabel")}</span>
            <span>-{formatJpy(clampedPoints, locale)}</span>
          </div>
        )}
        <div className="mt-1 flex items-center justify-between">
          <span className="text-sm text-[var(--color-ink-muted)]">{t("total")}</span>
          <span className="text-xl font-semibold text-[var(--color-primary)]">
            {formatJpy(totalAfterPoints, locale)}
          </span>
        </div>
      </div>

      <div className="mt-4 text-right">
        {user ? (
          <button
            type="button"
            onClick={onCheckout}
            disabled={submitting}
            className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)] disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting ? t("checkingOut") : t("checkout")}
          </button>
        ) : (
          <Link
            href="/signin?next=/cart"
            className="inline-block rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white hover:shadow-[var(--shadow-md)]"
          >
            {t("signInToCheckout")}
          </Link>
        )}
      </div>
    </div>
  );
}
