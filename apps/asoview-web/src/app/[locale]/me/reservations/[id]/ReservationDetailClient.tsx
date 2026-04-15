"use client";

import { Link, useRouter } from "@/i18n/navigation";
import {
  ApiError,
  NetworkError,
  type ReservationResponse,
  type ReservationStatusType,
  SignInRedirect,
  cancelReservation,
  getReservation,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useFormatter, useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

const STATUS_TONE: Record<ReservationStatusType, string> = {
  PENDING_APPROVAL: "bg-yellow-100 text-yellow-800",
  APPROVED: "bg-green-100 text-green-800",
  WAITLISTED: "bg-blue-100 text-blue-800",
  REJECTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-[var(--color-bg)] text-[var(--color-ink-muted)]",
  COMPLETED: "bg-green-50 text-green-700",
};

const CANCELLABLE: Set<ReservationStatusType> = new Set([
  "PENDING_APPROVAL",
  "APPROVED",
  "WAITLISTED",
]);

export function ReservationDetailClient({ reservationId }: { reservationId: string }) {
  const t = useTranslations("reservations");
  const format = useFormatter();
  const router = useRouter();
  const { ready, user } = useAuth();
  const modalRef = useRef<HTMLDialogElement>(null);
  const [reservation, setReservation] = useState<ReservationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelling, setCancelling] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = (msg: string) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToast(msg);
    toastTimerRef.current = setTimeout(() => setToast(null), 3000);
  };

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    };
  }, []);

  // Focus trap for cancel modal
  useEffect(() => {
    if (!showCancelModal || !modalRef.current) return;
    const dialog = modalRef.current;
    const focusable = dialog.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    first?.focus();

    function trapFocus(e: KeyboardEvent) {
      if (e.key !== "Tab") return;
      if (e.shiftKey) {
        if (document.activeElement === first) {
          e.preventDefault();
          last?.focus();
        }
      } else {
        if (document.activeElement === last) {
          e.preventDefault();
          first?.focus();
        }
      }
    }
    dialog.addEventListener("keydown", trapFocus);
    return () => dialog.removeEventListener("keydown", trapFocus);
  }, [showCancelModal]);

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      router.push(`/signin?next=${encodeURIComponent(`/me/reservations/${reservationId}`)}`);
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const r = await getReservation(reservationId, {
          signal: ctrl.signal,
          currentPath: `/me/reservations/${reservationId}`,
        });
        if (!cancelled) setReservation(r);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        if (e instanceof ApiError && e.status === 404) {
          setError(t("notFound"));
          return;
        }
        setError(e instanceof ApiError || e instanceof NetworkError ? e.message : t("loadError"));
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, reservationId, router, t]);

  async function handleCancel() {
    if (!reservation) return;
    setCancelling(true);
    try {
      const updated = await cancelReservation(reservation.reservationId, cancelReason.trim(), {
        currentPath: `/me/reservations/${reservationId}`,
      });
      setReservation(updated);
      setShowCancelModal(false);
      setCancelReason("");
      showToast(t("cancelSuccess"));
    } catch (e) {
      if (e instanceof SignInRedirect) {
        router.push(`/signin?next=${encodeURIComponent(e.next)}`);
        return;
      }
      showToast(e instanceof ApiError || e instanceof NetworkError ? e.message : t("cancelError"));
    } finally {
      setCancelling(false);
    }
  }

  if (error) {
    return (
      <div>
        <p role="alert" className="text-sm text-[var(--color-danger)]">
          {error}
        </p>
        <Link
          href="/me/reservations"
          className="mt-4 inline-block text-sm text-[var(--color-primary)]"
        >
          ← {t("pageTitle")}
        </Link>
      </div>
    );
  }
  if (!ready || !reservation) {
    return <p className="text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>;
  }

  const tone = STATUS_TONE[reservation.status] ?? "bg-[var(--color-bg)]";

  return (
    <>
      <Link href="/me/reservations" className="text-sm text-[var(--color-primary)]">
        ← {t("pageTitle")}
      </Link>

      <h1 className="mt-4 font-display text-2xl font-bold">{t("detailTitle")}</h1>

      <div className="mt-6 space-y-4 rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-6 shadow-[var(--shadow-sm)]">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-[var(--color-ink-muted)]">{t("status")}</span>
          <span
            className={`inline-block rounded-[var(--radius-sm)] px-2 py-0.5 text-xs font-semibold ${tone}`}
          >
            {t(`statusLabels.${reservation.status}`)}
          </span>
        </div>

        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-sm">
          <dt className="text-[var(--color-ink-muted)]">{t("guestName")}</dt>
          <dd>{reservation.guestName}</dd>

          <dt className="text-[var(--color-ink-muted)]">{t("guestEmail")}</dt>
          <dd>{reservation.guestEmail}</dd>

          <dt className="text-[var(--color-ink-muted)]">{t("guestCount")}</dt>
          <dd>{t("peopleSuffix", { n: reservation.guestCount })}</dd>

          <dt className="text-[var(--color-ink-muted)]">{t("slotId")}</dt>
          <dd className="font-mono text-xs">{reservation.slotId}</dd>

          <dt className="text-[var(--color-ink-muted)]">{t("createdAt")}</dt>
          <dd>
            {format.dateTime(new Date(reservation.createdAt), {
              dateStyle: "long",
              timeStyle: "short",
            })}
          </dd>

          {reservation.rejectReason && (
            <>
              <dt className="text-[var(--color-danger)]">{t("rejectReason")}</dt>
              <dd>{reservation.rejectReason}</dd>
            </>
          )}

          {reservation.cancelReason && (
            <>
              <dt className="text-[var(--color-ink-muted)]">{t("cancelReason")}</dt>
              <dd>{reservation.cancelReason}</dd>
            </>
          )}
        </dl>

        {CANCELLABLE.has(reservation.status) && (
          <button
            type="button"
            onClick={() => setShowCancelModal(true)}
            className="mt-2 rounded-[var(--radius-md)] bg-[var(--color-danger)] px-4 py-2 text-sm font-medium text-white hover:opacity-90"
          >
            {t("cancelButton")}
          </button>
        )}
      </div>

      {/* Cancel modal */}
      {showCancelModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
          onClick={() => setShowCancelModal(false)}
          onKeyDown={(e) => {
            if (e.key === "Escape") setShowCancelModal(false);
          }}
        >
          <dialog
            ref={modalRef}
            open
            aria-labelledby="cancel-modal-title"
            className="w-full max-w-md rounded-[var(--radius-lg)] bg-[var(--color-surface)] p-6 shadow-lg"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => {
              if (e.key === "Escape") setShowCancelModal(false);
            }}
          >
            <h2 id="cancel-modal-title" className="text-lg font-bold">
              {t("cancelButton")}
            </h2>
            <label className="mt-4 block text-sm">
              {t("cancelReasonLabel")}
              <textarea
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder={t("cancelReasonPlaceholder")}
                className="mt-1 block w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg)] p-2 text-sm"
                rows={3}
              />
            </label>
            <div className="mt-4 flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowCancelModal(false)}
                className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-4 py-2 text-sm"
              >
                {t("cancelDismiss")}
              </button>
              <button
                type="button"
                onClick={handleCancel}
                disabled={cancelling}
                className="rounded-[var(--radius-md)] bg-[var(--color-danger)] px-4 py-2 text-sm font-medium text-white hover:opacity-90 disabled:opacity-50"
              >
                {cancelling ? t("cancelling") : t("cancelConfirm")}
              </button>
            </div>
          </dialog>
        </div>
      )}

      {/* Toast */}
      {toast && (
        <div className="fixed bottom-4 right-4 z-50 rounded-[var(--radius-md)] bg-[var(--color-ink)] px-4 py-2 text-sm text-white shadow-lg">
          {toast}
        </div>
      )}
    </>
  );
}
