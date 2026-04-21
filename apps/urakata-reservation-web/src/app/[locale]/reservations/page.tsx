"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { StatusBadge } from "@/components/StatusBadge";
import { VenueSelector } from "@/components/VenueSelector";
import { Link } from "@/i18n/navigation";
import { api } from "@/lib/api";

type Reservation = {
  reservationId: string;
  venueId: string;
  slotId: string;
  status: string;
  guestName: string;
  guestEmail: string;
  guestCount: number;
  createdAt: string;
};

const STATUS_TABS = [
  "ALL",
  "PENDING_APPROVAL",
  "APPROVED",
  "WAITLISTED",
  "REJECTED",
  "CANCELLED",
] as const;

export default function ReservationsPage() {
  const t = useTranslations("reservations");
  const ts = useTranslations("status");
  const tc = useTranslations("common");
  const [venueId, setVenueId] = useState("");
  const [status, setStatus] = useState<string>("ALL");
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchReservations = useCallback(async () => {
    if (!venueId) return;
    setLoading(true);
    try {
      const params = new URLSearchParams({ venueId });
      if (status !== "ALL") {
        params.set("status", status);
      }
      const data = await api.get<Reservation[]>(`/v1/op/reservations?${params.toString()}`);
      setReservations(data);
    } catch {
      setReservations([]);
    } finally {
      setLoading(false);
    }
  }, [venueId, status]);

  useEffect(() => {
    fetchReservations();
  }, [fetchReservations]);

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">{t("title")}</h1>

      <div className="flex gap-4 mb-4">
        <VenueSelector value={venueId} onChange={setVenueId} />
      </div>

      <div className="flex gap-1 mb-6 overflow-x-auto">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setStatus(tab)}
            className={`px-3 py-1.5 text-sm rounded-full whitespace-nowrap transition-colors ${
              status === tab
                ? "bg-[var(--color-primary)] text-white"
                : "bg-white border border-[var(--color-border)] text-[var(--color-text-muted)] hover:bg-[var(--color-surface-alt)]"
            }`}
          >
            {tab === "ALL" ? t("all") : ts(tab as "PENDING_APPROVAL")}
          </button>
        ))}
      </div>

      {loading && <p className="text-[var(--color-text-muted)]">{tc("loading")}</p>}

      {!loading && reservations.length === 0 && venueId && (
        <p className="text-[var(--color-text-muted)]">{tc("noData")}</p>
      )}

      <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-[var(--color-surface-alt)] border-b border-[var(--color-border)]">
            <tr>
              <th className="text-left px-4 py-3 font-medium">{t("guestName")}</th>
              <th className="text-left px-4 py-3 font-medium">{t("guestEmail")}</th>
              <th className="text-left px-4 py-3 font-medium">{t("guestCount")}</th>
              <th className="text-left px-4 py-3 font-medium">{t("status")}</th>
              <th className="text-left px-4 py-3 font-medium">{t("createdAt")}</th>
            </tr>
          </thead>
          <tbody>
            {reservations.map((r) => (
              <tr
                key={r.reservationId}
                className="border-b border-[var(--color-border)] hover:bg-[var(--color-surface-alt)] transition-colors"
              >
                <td className="px-4 py-3">
                  <Link
                    href={`/reservations/${r.reservationId}`}
                    className="text-[var(--color-primary)] hover:underline"
                  >
                    {r.guestName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-[var(--color-text-muted)]">{r.guestEmail}</td>
                <td className="px-4 py-3">{r.guestCount}</td>
                <td className="px-4 py-3">
                  <StatusBadge status={r.status} />
                </td>
                <td className="px-4 py-3 text-[var(--color-text-muted)]">
                  {new Date(r.createdAt).toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
