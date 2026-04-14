"use client";

import { VenueSelector } from "@/components/VenueSelector";
import { Link } from "@/i18n/navigation";
import { ApiError, api } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";

function localToday(): string {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

type Slot = {
  slotId: string;
  venueId: string;
  productId: string;
  slotDate: string;
  startTime: string;
  endTime: string;
  capacity: number;
  approvedCount: number;
  waitlistCount: number;
};

export default function SlotsPage() {
  const t = useTranslations("slots");
  const tc = useTranslations("common");
  const [venueId, setVenueId] = useState("");
  const [date, setDate] = useState(localToday);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null);

  const fetchSlots = useCallback(async () => {
    if (!venueId || !date) return;
    setLoading(true);
    setFetchError(false);
    try {
      const data = await api.get<Slot[]>(
        `/v1/op/reservation-slots?venueId=${encodeURIComponent(venueId)}&date=${encodeURIComponent(date)}`,
      );
      setSlots(data);
    } catch {
      setSlots([]);
      setFetchError(true);
    } finally {
      setLoading(false);
    }
  }, [venueId, date]);

  useEffect(() => {
    fetchSlots();
  }, [fetchSlots]);

  const handleDelete = async (slotId: string) => {
    try {
      await api.delete(`/v1/op/reservation-slots/${slotId}`);
      setDeleteConfirm(null);
      fetchSlots();
    } catch (err) {
      setDeleteConfirm(null);
      if (err instanceof ApiError && err.status === 409) {
        alert(t("blocked"));
      } else {
        alert(tc("error"));
      }
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t("title")}</h1>
        <Link
          href="/slots/new"
          className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] hover:bg-[var(--color-primary-hover)] text-sm transition-colors"
        >
          {t("create")}
        </Link>
      </div>

      <div className="flex gap-4 mb-6">
        <VenueSelector value={venueId} onChange={setVenueId} />
        <input
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
        />
      </div>

      {loading && <p className="text-[var(--color-text-muted)]">{tc("loading")}</p>}

      {!loading && fetchError && venueId && (
        <div className="text-center py-8">
          <p className="text-[var(--color-danger)] mb-3">{tc("error")}</p>
          <button
            type="button"
            onClick={fetchSlots}
            className="px-4 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
          >
            {tc("retry")}
          </button>
        </div>
      )}

      {!loading && !fetchError && slots.length === 0 && venueId && (
        <p className="text-[var(--color-text-muted)]">{tc("noData")}</p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {slots.map((slot) => {
          const utilization = slot.capacity > 0 ? (slot.approvedCount / slot.capacity) * 100 : 0;
          return (
            <div
              key={slot.slotId}
              className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-4"
            >
              <div className="flex justify-between items-start mb-3">
                <div>
                  <p className="font-medium">
                    {slot.startTime} - {slot.endTime}
                  </p>
                  <p className="text-sm text-[var(--color-text-muted)]">{slot.slotDate}</p>
                </div>
                <div className="flex gap-2">
                  <Link
                    href={`/slots/${slot.slotId}/edit?startTime=${encodeURIComponent(slot.startTime)}&endTime=${encodeURIComponent(slot.endTime)}&capacity=${slot.capacity}`}
                    className="text-sm text-[var(--color-primary)] hover:underline"
                  >
                    {t("edit")}
                  </Link>
                  <button
                    type="button"
                    onClick={() => setDeleteConfirm(slot.slotId)}
                    className="text-sm text-[var(--color-danger)] hover:underline"
                  >
                    {t("delete")}
                  </button>
                </div>
              </div>

              <div className="mb-2">
                <div className="flex justify-between text-sm mb-1">
                  <span>
                    {t("approved")}: {slot.approvedCount}/{slot.capacity}
                  </span>
                  <span>{Math.round(utilization)}%</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2">
                  <div
                    className="bg-[var(--color-primary)] h-2 rounded-full transition-all"
                    style={{ width: `${Math.min(100, utilization)}%` }}
                  />
                </div>
              </div>

              {slot.waitlistCount > 0 && (
                <p className="text-sm text-[var(--color-warning)]">
                  {t("waitlisted")}: {slot.waitlistCount}
                </p>
              )}

              {deleteConfirm === slot.slotId && (
                <div className="mt-3 p-3 bg-red-50 rounded-[var(--radius-md)]">
                  <p className="text-sm text-[var(--color-danger)] mb-2">{t("deleteConfirm")}</p>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => handleDelete(slot.slotId)}
                      className="px-3 py-1 bg-[var(--color-danger)] text-white rounded text-sm"
                    >
                      {t("delete")}
                    </button>
                    <button
                      type="button"
                      onClick={() => setDeleteConfirm(null)}
                      className="px-3 py-1 border rounded text-sm"
                    >
                      {t("cancel")}
                    </button>
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
