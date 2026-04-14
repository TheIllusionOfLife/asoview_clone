"use client";

import { ReasonModal } from "@/components/ReasonModal";
import { StatusBadge } from "@/components/StatusBadge";
import { Link } from "@/i18n/navigation";
import { ApiError, api } from "@/lib/api";
import { useTranslations } from "next-intl";
import { use, useCallback, useEffect, useRef, useState } from "react";

type Reservation = {
  reservationId: string;
  tenantId: string;
  venueId: string;
  slotId: string;
  consumerUserId: string;
  status: string;
  guestName: string;
  guestEmail: string;
  guestCount: number;
  rejectReason: string | null;
  cancelReason: string | null;
  createdAt: string;
  updatedAt: string;
};

type AuditEntry = {
  logId: string;
  reservationId: string;
  action: string;
  actorUserId: string | null;
  reason: string | null;
  createdAt: string;
};

const TERMINAL_STATUSES = ["REJECTED", "CANCELLED", "COMPLETED"];

export default function ReservationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const t = useTranslations("reservations");
  const tc = useTranslations("common");
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [modal, setModal] = useState<"reject" | "cancel" | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [toast, setToast] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [res, logs] = await Promise.all([
        api.get<Reservation>(`/v1/op/reservations/${id}`),
        api.get<AuditEntry[]>(`/v1/op/reservations/${id}/audit`),
      ]);
      setReservation(res);
      setAudit(logs);
    } catch {
      setReservation(null);
      setAudit([]);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    };
  }, []);

  const showToast = (msg: string) => {
    if (toastTimerRef.current) clearTimeout(toastTimerRef.current);
    setToast(msg);
    toastTimerRef.current = setTimeout(() => setToast(null), 3000);
  };

  const handleAction = async (
    action: "approve" | "waitlist" | "reject" | "cancel",
    reason?: string,
  ) => {
    setActionLoading(true);
    try {
      if (action === "approve") {
        await api.put(`/v1/op/reservations/${id}/approve`);
      } else if (action === "waitlist") {
        await api.put(`/v1/op/reservations/${id}/waitlist`);
      } else if (action === "reject") {
        await api.put(`/v1/op/reservations/${id}/reject`, { reason });
      } else {
        await api.put(`/v1/op/reservations/${id}/cancel`, { reason });
      }
      setModal(null);
      showToast(tc("success"));
      fetchData();
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : tc("error");
      showToast(msg);
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return <p className="text-[var(--color-text-muted)]">{tc("loading")}</p>;
  }

  if (!reservation) {
    return <p className="text-[var(--color-text-muted)]">{tc("noData")}</p>;
  }

  const isTerminal = TERMINAL_STATUSES.includes(reservation.status);

  return (
    <div className="max-w-2xl">
      <div className="mb-4">
        <Link href="/reservations" className="text-sm text-[var(--color-primary)] hover:underline">
          {tc("back")}
        </Link>
      </div>

      <h1 className="text-2xl font-bold mb-6">{t("detail")}</h1>

      <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-6 mb-6">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <p className="text-sm text-[var(--color-text-muted)]">{t("guestName")}</p>
            <p className="font-medium">{reservation.guestName}</p>
          </div>
          <div>
            <p className="text-sm text-[var(--color-text-muted)]">{t("guestEmail")}</p>
            <p className="font-medium">{reservation.guestEmail}</p>
          </div>
          <div>
            <p className="text-sm text-[var(--color-text-muted)]">{t("guestCount")}</p>
            <p className="font-medium">{reservation.guestCount}</p>
          </div>
          <div>
            <p className="text-sm text-[var(--color-text-muted)]">{t("status")}</p>
            <StatusBadge status={reservation.status} />
          </div>
          <div>
            <p className="text-sm text-[var(--color-text-muted)]">{t("createdAt")}</p>
            <p className="font-medium">{new Date(reservation.createdAt).toLocaleString()}</p>
          </div>
          <div>
            <p className="text-sm text-[var(--color-text-muted)]">{t("slotId")}</p>
            <p className="font-medium text-sm">{reservation.slotId}</p>
          </div>
        </div>

        {reservation.rejectReason && (
          <div className="mt-4 p-3 bg-red-50 rounded-[var(--radius-md)]">
            <p className="text-sm text-[var(--color-danger)]">
              {t("reason")}: {reservation.rejectReason}
            </p>
          </div>
        )}
        {reservation.cancelReason && (
          <div className="mt-4 p-3 bg-gray-50 rounded-[var(--radius-md)]">
            <p className="text-sm text-[var(--color-text-muted)]">
              {t("reason")}: {reservation.cancelReason}
            </p>
          </div>
        )}
      </div>

      {!isTerminal && (
        <div className="flex gap-3 mb-6">
          {(reservation.status === "PENDING_APPROVAL" || reservation.status === "WAITLISTED") && (
            <button
              type="button"
              onClick={() => handleAction("approve")}
              disabled={actionLoading}
              className="px-4 py-2 bg-[var(--color-success)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
            >
              {t("approve")}
            </button>
          )}
          {reservation.status === "PENDING_APPROVAL" && (
            <button
              type="button"
              onClick={() => handleAction("waitlist")}
              disabled={actionLoading}
              className="px-4 py-2 bg-[var(--color-warning)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
            >
              {t("waitlist")}
            </button>
          )}
          {reservation.status === "PENDING_APPROVAL" && (
            <button
              type="button"
              onClick={() => setModal("reject")}
              disabled={actionLoading}
              className="px-4 py-2 bg-[var(--color-danger)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
            >
              {t("reject")}
            </button>
          )}
          <button
            type="button"
            onClick={() => setModal("cancel")}
            disabled={actionLoading}
            className="px-4 py-2 border border-[var(--color-danger)] text-[var(--color-danger)] rounded-[var(--radius-md)] text-sm disabled:opacity-50"
          >
            {t("cancel")}
          </button>
        </div>
      )}

      <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-6">
        <h2 className="text-lg font-bold mb-4">{t("auditLog")}</h2>
        {audit.length === 0 ? (
          <p className="text-[var(--color-text-muted)]">{t("noActions")}</p>
        ) : (
          <div className="space-y-3">
            {audit.map((entry) => (
              <div
                key={entry.logId}
                className="flex items-start gap-3 pb-3 border-b border-[var(--color-border)] last:border-0"
              >
                <div className="w-2 h-2 mt-2 rounded-full bg-[var(--color-primary)] shrink-0" />
                <div>
                  <p className="text-sm font-medium">{entry.action}</p>
                  {entry.reason && (
                    <p className="text-sm text-[var(--color-text-muted)]">{entry.reason}</p>
                  )}
                  <p className="text-xs text-[var(--color-text-muted)]">
                    {new Date(entry.createdAt).toLocaleString()}
                    {entry.actorUserId && ` — ${t("byActor", { actor: entry.actorUserId })}`}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {modal === "reject" && (
        <ReasonModal
          action={t("reject")}
          onConfirm={(reason) => handleAction("reject", reason)}
          onCancel={() => setModal(null)}
        />
      )}
      {modal === "cancel" && (
        <ReasonModal
          action={t("cancel")}
          onConfirm={(reason) => handleAction("cancel", reason)}
          onCancel={() => setModal(null)}
        />
      )}

      {toast && (
        <div className="fixed bottom-6 right-6 z-50 px-4 py-2 bg-gray-900 text-white rounded-[var(--radius-md)] shadow-lg text-sm">
          {toast}
        </div>
      )}
    </div>
  );
}
