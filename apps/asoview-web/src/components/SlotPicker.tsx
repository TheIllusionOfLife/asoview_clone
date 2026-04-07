"use client";

/**
 * Live availability picker for a product detail page.
 *
 * Fetches `GET /v1/products/{productId}/availability?from=&to=` for a
 * 14-day default window. Provides advance/retreat controls, a slot grid,
 * and a quantity selector. On "Book", routes to /signin?next=… if the
 * user is unauthenticated, otherwise mints an Idempotency-Key from the
 * `{productId, slotId, quantity}` fingerprint and POSTs `/v1/orders`.
 *
 * Accessibility: the slot grid is a `role="radiogroup"`, slots are
 * `role="radio"` with `aria-checked`, keyboard activation via space/enter.
 */

import { ApiError, NetworkError, SignInRedirect, SlotTakenError, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { clearIdempotencyKey } from "@/lib/idempotency";
import type {
  AvailabilityEntry,
  CreateOrderRequest,
  OrderResponse,
  ProductResponse,
} from "@/lib/types";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

const WINDOW_DAYS = 14;

function isoDate(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addDays(base: Date, days: number): Date {
  const d = new Date(base);
  d.setUTCDate(d.getUTCDate() + days);
  return d;
}

function formatJpDate(iso: string): string {
  // YYYY-MM-DD → "M月D日 (曜)"
  const [y, m, d] = iso.split("-").map(Number);
  if (!y || !m || !d) return iso;
  const date = new Date(Date.UTC(y, m - 1, d));
  const wd = ["日", "月", "火", "水", "木", "金", "土"][date.getUTCDay()];
  return `${m}月${d}日 (${wd})`;
}

function shortTime(t: string): string {
  // HH:mm:ss → HH:mm
  return t.slice(0, 5);
}

export function SlotPicker({ product }: { product: ProductResponse }) {
  const router = useRouter();
  const { user, ready } = useAuth();
  const [windowStart, setWindowStart] = useState<Date>(() => {
    const d = new Date();
    d.setUTCHours(0, 0, 0, 0);
    return d;
  });
  const [entries, setEntries] = useState<AvailabilityEntry[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [bookError, setBookError] = useState<string | null>(null);

  const from = useMemo(() => isoDate(windowStart), [windowStart]);
  const to = useMemo(() => isoDate(addDays(windowStart, WINDOW_DAYS - 1)), [windowStart]);

  useEffect(() => {
    let cancelled = false;
    const ctrl = new AbortController();
    setLoading(true);
    setLoadError(null);
    setSelectedSlotId(null);
    api
      .get<AvailabilityEntry[]>(
        `/v1/products/${encodeURIComponent(product.id)}/availability?from=${from}&to=${to}`,
        { signal: ctrl.signal, retries: 1 },
      )
      .then((data) => {
        if (cancelled) return;
        setEntries(data);
        setLoading(false);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        if (e instanceof NetworkError && e.message.includes("Aborted")) return;
        setLoading(false);
        setLoadError(
          e instanceof ApiError || e instanceof NetworkError
            ? e.message
            : "空き状況を取得できませんでした",
        );
      });
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [product.id, from, to]);

  const slotsByDate = useMemo(() => {
    const map = new Map<string, AvailabilityEntry[]>();
    for (const e of entries ?? []) {
      const arr = map.get(e.date) ?? [];
      arr.push(e);
      map.set(e.date, arr);
    }
    for (const arr of map.values()) {
      arr.sort((a, b) => a.startTime.localeCompare(b.startTime));
    }
    return map;
  }, [entries]);

  const orderedDates = useMemo(() => {
    return Array.from(slotsByDate.keys()).sort();
  }, [slotsByDate]);

  const selected = useMemo(
    () => entries?.find((e) => e.slotId === selectedSlotId) ?? null,
    [entries, selectedSlotId],
  );

  const maxQty = selected ? Math.max(1, Math.min(10, selected.remaining)) : 10;

  const onBook = useCallback(async () => {
    if (!selected) return;
    setBookError(null);
    if (!ready) return;
    if (!user) {
      router.push(`/signin?next=${encodeURIComponent(`/products/${product.id}`)}`);
      return;
    }
    setSubmitting(true);
    const fp = { productId: product.id, slotId: selected.slotId, quantity };
    const body: CreateOrderRequest = {
      items: [
        {
          productVariantId: selected.productVariantId,
          slotId: selected.slotId,
          quantity,
        },
      ],
    };
    try {
      const order = await api.post<OrderResponse>("/v1/orders", body, { idempotency: fp });
      router.push(`/checkout/${order.orderId}`);
    } catch (e: unknown) {
      setSubmitting(false);
      if (e instanceof SignInRedirect) {
        router.push(`/signin?next=${encodeURIComponent(e.next)}`);
        return;
      }
      if (e instanceof SlotTakenError) {
        // Clear the idempotency key so the next attempt with a new slot
        // is treated as a fresh booking, not a duplicate.
        clearIdempotencyKey(fp);
        setBookError("この時間帯は満席になりました。別の時間帯を選んでください。");
        // Refresh availability so the user sees the new state.
        setSelectedSlotId(null);
        setEntries((prev) =>
          prev
            ? prev.map((e) => (e.slotId === selected.slotId ? { ...e, remaining: 0 } : e))
            : prev,
        );
        return;
      }
      setBookError(
        e instanceof ApiError || e instanceof NetworkError
          ? e.message
          : "予約処理中にエラーが発生しました",
      );
    }
  }, [product.id, quantity, ready, user, router, selected]);

  return (
    <section
      aria-labelledby="slot-picker-heading"
      className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-sm)]"
    >
      <h2 id="slot-picker-heading" className="font-display text-xl font-semibold">
        空き状況・予約
      </h2>

      <div className="mt-3 flex items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => setWindowStart((d) => addDays(d, -WINDOW_DAYS))}
          className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 text-sm hover:border-[var(--color-primary)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]"
          aria-label="前の14日間を表示"
        >
          ← 前の14日間
        </button>
        <span className="text-sm text-[var(--color-ink-muted)]">
          {formatJpDate(from)} 〜 {formatJpDate(to)}
        </span>
        <button
          type="button"
          onClick={() => setWindowStart((d) => addDays(d, WINDOW_DAYS))}
          className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 text-sm hover:border-[var(--color-primary)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]"
          aria-label="次の14日間を表示"
        >
          次の14日間 →
        </button>
      </div>

      {loading && (
        <div className="mt-4 grid grid-cols-2 sm:grid-cols-3 gap-2" aria-live="polite">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              // biome-ignore lint/suspicious/noArrayIndexKey: skeleton placeholders
              key={i}
              className="h-12 rounded-[var(--radius-md)] bg-[var(--color-border)]/40 animate-pulse"
            />
          ))}
        </div>
      )}

      {!loading && loadError && (
        <p className="mt-4 text-sm text-[var(--color-danger)]">{loadError}</p>
      )}

      {!loading && !loadError && orderedDates.length === 0 && (
        <p className="mt-4 text-sm text-[var(--color-ink-muted)]">
          この期間に空き枠はありません。期間を変更してください。
        </p>
      )}

      {!loading && !loadError && orderedDates.length > 0 && (
        <div role="radiogroup" aria-label="予約可能な時間枠" className="mt-4 space-y-4">
          {orderedDates.map((date) => (
            <div key={date}>
              <p className="text-sm font-semibold">{formatJpDate(date)}</p>
              <div className="mt-1.5 grid grid-cols-2 sm:grid-cols-3 gap-2">
                {(slotsByDate.get(date) ?? []).map((s) => {
                  const disabled = s.remaining <= 0;
                  const checked = s.slotId === selectedSlotId;
                  return (
                    <button
                      key={s.slotId}
                      type="button"
                      // biome-ignore lint/a11y/useSemanticElements: button is needed for disabled+keyboard activation; radio role conveys grouping
                      role="radio"
                      aria-checked={checked}
                      aria-disabled={disabled}
                      disabled={disabled}
                      onClick={() => {
                        if (disabled) return;
                        setSelectedSlotId(s.slotId);
                        setQuantity((q) => Math.min(q, Math.max(1, s.remaining)));
                      }}
                      className={[
                        "rounded-[var(--radius-md)] border px-3 py-2 text-sm text-left transition",
                        "focus-visible:outline-2 focus-visible:outline-[var(--color-primary)]",
                        disabled
                          ? "border-[var(--color-border)] bg-[var(--color-border)]/20 text-[var(--color-ink-muted)] cursor-not-allowed"
                          : checked
                            ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
                            : "border-[var(--color-border)] hover:border-[var(--color-primary)]",
                      ].join(" ")}
                    >
                      <span className="block font-semibold">
                        {shortTime(s.startTime)}–{shortTime(s.endTime)}
                      </span>
                      <span className="block text-xs">
                        {disabled ? "満席" : `残り ${s.remaining}`}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="mt-6 flex flex-col sm:flex-row sm:items-end gap-3">
        <label className="text-sm">
          <span className="block text-[var(--color-ink-muted)]">人数</span>
          <select
            value={quantity}
            onChange={(e) => setQuantity(Number(e.target.value))}
            disabled={!selected}
            className="mt-1 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-[var(--color-primary)] disabled:opacity-50"
          >
            {Array.from({ length: maxQty }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n}名
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          onClick={onBook}
          disabled={!selected || submitting || !ready}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] focus-visible:outline-2 focus-visible:outline-[var(--color-primary)] disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? "予約中…" : "予約する"}
        </button>
      </div>

      {bookError && (
        <p role="alert" className="mt-3 text-sm text-[var(--color-danger)]">
          {bookError}
        </p>
      )}
    </section>
  );
}
