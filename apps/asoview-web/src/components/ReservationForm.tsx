"use client";

import { usePathname } from "next/navigation";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "@/i18n/navigation";
import {
  ApiError,
  listAvailableSlots,
  NetworkError,
  requestReservation,
  SignInRedirect,
  type SlotAvailability,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { addDaysIso, todayIsoJst } from "@/lib/slot-date";

const IDEM_STORAGE_PREFIX = "asoview:idem:rsv:";

function getOrCreateReservationIdempotencyKey(slotId: string, uid: string): string {
  const storageKey = `${IDEM_STORAGE_PREFIX}${slotId}|${uid}`;
  if (typeof sessionStorage !== "undefined") {
    const existing = sessionStorage.getItem(storageKey);
    if (existing) return existing;
  }
  const key = crypto.randomUUID();
  if (typeof sessionStorage !== "undefined") {
    sessionStorage.setItem(storageKey, key);
  }
  return key;
}

function clearReservationIdempotencyKey(slotId: string, uid: string): void {
  if (typeof sessionStorage === "undefined") return;
  sessionStorage.removeItem(`${IDEM_STORAGE_PREFIX}${slotId}|${uid}`);
}

function shortTime(t: string): string {
  return t.slice(0, 5);
}

export function ReservationForm({ venueId }: { venueId: string }) {
  const t = useTranslations("reservationForm");
  const router = useRouter();
  const { user, ready } = useAuth();
  const pathname = usePathname();

  const [date, setDate] = useState(() => todayIsoJst());
  const [slots, setSlots] = useState<SlotAvailability[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null);

  const [guestName, setGuestName] = useState("");
  const [guestEmail, setGuestEmail] = useState("");
  const [guestCount, setGuestCount] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Pre-fill from Firebase profile
  useEffect(() => {
    if (user) {
      if (user.displayName && !guestName) setGuestName(user.displayName);
      if (user.email && !guestEmail) setGuestEmail(user.email);
    }
  }, [user, guestName, guestEmail]);

  // Fetch slots for selected date
  useEffect(() => {
    let cancelled = false;
    const ctrl = new AbortController();
    setLoading(true);
    setLoadError(null);
    setSelectedSlotId(null);

    listAvailableSlots(venueId, date, { signal: ctrl.signal })
      .then((data) => {
        if (!cancelled) {
          setSlots(data);
          setLoading(false);
        }
      })
      .catch((e) => {
        if (cancelled || ctrl.signal.aborted) return;
        setLoading(false);
        setLoadError(
          e instanceof ApiError || e instanceof NetworkError ? e.message : t("loadError"),
        );
      });

    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [venueId, date, t]);

  const selected = useMemo(
    () => slots?.find((s) => s.slotId === selectedSlotId) ?? null,
    [slots, selectedSlotId],
  );

  const maxQty = selected ? Math.max(1, Math.min(10, selected.remainingCapacity)) : 10;

  const onSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      if (!selected || !guestName.trim() || !guestEmail.trim()) return;

      if (!ready) return;
      if (!user) {
        router.push("/signin?next=/me/reservations");
        return;
      }

      setSubmitting(true);
      setSubmitError(null);

      try {
        const idempotencyKey = getOrCreateReservationIdempotencyKey(selected.slotId, user.uid);
        const reservation = await requestReservation(
          {
            slotId: selected.slotId,
            idempotencyKey,
            guestName: guestName.trim(),
            guestEmail: guestEmail.trim(),
            guestCount,
          },
          { currentPath: pathname },
        );
        clearReservationIdempotencyKey(selected.slotId, user.uid);
        router.push(`/me/reservations/${reservation.reservationId}`);
      } catch (err) {
        setSubmitting(false);
        if (err instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(err.next)}`);
          return;
        }
        setSubmitError(
          err instanceof ApiError || err instanceof NetworkError ? err.message : t("submitError"),
        );
      }
    },
    [selected, guestName, guestEmail, guestCount, ready, user, router, t, pathname],
  );

  return (
    <section className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-sm)]">
      <h2 className="font-display text-xl font-semibold">{t("title")}</h2>

      {/* Date picker */}
      <div className="mt-4 flex items-center gap-3">
        <button
          type="button"
          onClick={() => setDate((d) => addDaysIso(d, -1))}
          aria-label={t("previousDay")}
          className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 text-sm hover:border-[var(--color-primary)]"
        >
          ←
        </button>
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-1.5 text-sm"
          aria-label={t("dateLabel")}
        />
        <button
          type="button"
          onClick={() => setDate((d) => addDaysIso(d, 1))}
          aria-label={t("nextDay")}
          className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 text-sm hover:border-[var(--color-primary)]"
        >
          →
        </button>
      </div>

      {/* Slot grid */}
      {loading && <p className="mt-4 text-sm text-[var(--color-ink-muted)]">{t("loadingSlots")}</p>}
      {!loading && loadError && (
        <p className="mt-4 text-sm text-[var(--color-danger)]">{loadError}</p>
      )}
      {!loading && !loadError && slots?.length === 0 && (
        <p className="mt-4 text-sm text-[var(--color-ink-muted)]">{t("noSlots")}</p>
      )}
      {!loading && !loadError && slots && slots.length > 0 && (
        <div
          role="radiogroup"
          aria-label={t("slotGroupLabel")}
          className="mt-4 grid grid-cols-2 sm:grid-cols-3 gap-2"
        >
          {slots.map((s) => {
            const disabled = s.remainingCapacity <= 0;
            const checked = s.slotId === selectedSlotId;
            return (
              <label
                key={s.slotId}
                className={[
                  "rounded-[var(--radius-md)] border px-3 py-2 text-sm text-left cursor-pointer",
                  disabled
                    ? "border-[var(--color-border)] bg-[var(--color-border)]/20 text-[var(--color-ink-muted)] cursor-not-allowed"
                    : checked
                      ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
                      : "border-[var(--color-border)] hover:border-[var(--color-primary)]",
                ].join(" ")}
              >
                <input
                  type="radio"
                  name="slot"
                  value={s.slotId}
                  checked={checked}
                  disabled={disabled}
                  onChange={() => {
                    setSelectedSlotId(s.slotId);
                    setGuestCount((q) => Math.min(q, Math.max(1, s.remainingCapacity)));
                  }}
                  className="sr-only"
                />
                <span className="block font-semibold">
                  {shortTime(s.startTime)}–{shortTime(s.endTime)}
                </span>
                <span className="block text-xs">
                  {disabled ? t("full") : t("remaining", { n: s.remainingCapacity })}
                </span>
              </label>
            );
          })}
        </div>
      )}

      {/* Request form */}
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <label className="block text-sm">
          <span className="text-[var(--color-ink-muted)]">{t("nameLabel")}</span>
          <input
            type="text"
            required
            value={guestName}
            onChange={(e) => setGuestName(e.target.value)}
            className="mt-1 block w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-sm"
          />
        </label>
        <label className="block text-sm">
          <span className="text-[var(--color-ink-muted)]">{t("emailLabel")}</span>
          <input
            type="email"
            required
            value={guestEmail}
            onChange={(e) => setGuestEmail(e.target.value)}
            className="mt-1 block w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-sm"
          />
        </label>
        <label className="block text-sm">
          <span className="text-[var(--color-ink-muted)]">{t("guestCountLabel")}</span>
          <select
            value={guestCount}
            onChange={(e) => setGuestCount(Number(e.target.value))}
            disabled={!selected}
            className="mt-1 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 text-sm disabled:opacity-50"
          >
            {Array.from({ length: maxQty }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>
                {n}
                {t("peopleSuffix")}
              </option>
            ))}
          </select>
        </label>

        <button
          type="submit"
          disabled={!selected || submitting || !ready || !guestName.trim() || !guestEmail.trim()}
          className="w-full rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? t("submitting") : t("submitButton")}
        </button>

        {submitError && (
          <p role="alert" className="text-sm text-[var(--color-danger)]">
            {submitError}
          </p>
        )}
      </form>
    </section>
  );
}
