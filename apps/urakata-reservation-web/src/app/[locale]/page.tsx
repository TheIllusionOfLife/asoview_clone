"use client";

import { VenueSelector } from "@/components/VenueSelector";
import { Link } from "@/i18n/navigation";
import { api } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";

type DashboardSummary = {
  reservationCounts: Record<string, number>;
  slotUtilization: {
    totalSlots: number;
    totalCapacity: number;
    totalApproved: number;
  };
};

export default function DashboardPage() {
  const t = useTranslations("dashboard");
  const ta = useTranslations("app");
  const tc = useTranslations("common");
  const [venueId, setVenueId] = useState("");
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const fetchDashboard = useCallback(async () => {
    if (!venueId) return;
    setLoading(true);
    setError(false);
    try {
      const data = await api.get<DashboardSummary>(
        `/v1/op/dashboard?venueId=${encodeURIComponent(venueId)}`,
      );
      setSummary(data);
    } catch {
      setSummary(null);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [venueId]);

  useEffect(() => {
    fetchDashboard();
  }, [fetchDashboard]);

  const counts = summary?.reservationCounts ?? {};
  const util = summary?.slotUtilization;
  const utilPercent =
    util && util.totalCapacity > 0
      ? Math.round((util.totalApproved / util.totalCapacity) * 100)
      : 0;

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">{t("title")}</h1>

      <div className="mb-6">
        <VenueSelector value={venueId} onChange={setVenueId} />
      </div>

      {loading && <p className="text-[var(--color-text-muted)]">{tc("loading")}</p>}

      {!loading && venueId && summary && (
        <>
          <div className="grid gap-4 sm:grid-cols-3 mb-6">
            <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-5">
              <p className="text-sm text-[var(--color-text-muted)] mb-1">{t("pending")}</p>
              <p className="text-3xl font-bold text-[var(--color-warning)]">
                {counts.PENDING_APPROVAL ?? 0}
              </p>
            </div>
            <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-5">
              <p className="text-sm text-[var(--color-text-muted)] mb-1">{t("approved")}</p>
              <p className="text-3xl font-bold text-[var(--color-success)]">
                {counts.APPROVED ?? 0}
              </p>
            </div>
            <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-5">
              <p className="text-sm text-[var(--color-text-muted)] mb-1">{t("waitlisted")}</p>
              <p className="text-3xl font-bold text-[var(--color-primary)]">
                {counts.WAITLISTED ?? 0}
              </p>
            </div>
          </div>

          {util && (
            <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-5 mb-6">
              <h2 className="text-lg font-bold mb-3">{t("slotUtilization")}</h2>
              <div className="grid grid-cols-3 gap-4 mb-3">
                <div>
                  <p className="text-sm text-[var(--color-text-muted)]">{t("totalSlots")}</p>
                  <p className="text-xl font-bold">{util.totalSlots}</p>
                </div>
                <div>
                  <p className="text-sm text-[var(--color-text-muted)]">{t("totalCapacity")}</p>
                  <p className="text-xl font-bold">{util.totalCapacity}</p>
                </div>
                <div>
                  <p className="text-sm text-[var(--color-text-muted)]">{t("totalApproved")}</p>
                  <p className="text-xl font-bold">{util.totalApproved}</p>
                </div>
              </div>
              <div className="w-full bg-gray-200 rounded-full h-3">
                <div
                  className="bg-[var(--color-primary)] h-3 rounded-full transition-all"
                  style={{ width: `${Math.min(100, utilPercent)}%` }}
                />
              </div>
              <p className="text-sm text-[var(--color-text-muted)] mt-1">{utilPercent}%</p>
            </div>
          )}

          <div className="flex gap-4">
            <Link
              href="/slots"
              className="px-4 py-2 bg-white border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm hover:bg-[var(--color-surface-alt)] transition-colors"
            >
              {ta("slots")}
            </Link>
            <Link
              href="/reservations"
              className="px-4 py-2 bg-white border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm hover:bg-[var(--color-surface-alt)] transition-colors"
            >
              {ta("reservations")}
            </Link>
          </div>
        </>
      )}

      {!loading && error && venueId && (
        <div className="text-center py-8">
          <p className="text-[var(--color-danger)] mb-3">{tc("error")}</p>
          <button
            type="button"
            onClick={fetchDashboard}
            className="px-4 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
          >
            {tc("retry")}
          </button>
        </div>
      )}

      {!loading && !venueId && <p className="text-[var(--color-text-muted)]">{t("selectVenue")}</p>}
    </div>
  );
}
