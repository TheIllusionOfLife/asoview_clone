"use client";

import { useTranslations } from "next-intl";
import { api, ApiError } from "@/lib/api";
import { useRouter } from "@/i18n/navigation";
import { useEffect, useState } from "react";
import { use } from "react";

type Slot = {
  slotId: string;
  startTime: string;
  endTime: string;
  capacity: number;
  approvedCount: number;
};

export default function EditSlotPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const t = useTranslations("slots");
  const tc = useTranslations("common");
  const router = useRouter();
  const [slot, setSlot] = useState<Slot | null>(null);
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [capacity, setCapacity] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // Fetch slot by listing all slots and finding the one with matching ID
    // (no direct GET by ID endpoint exists, so we load from slot list context)
    // For now, we use a simple approach: the edit page gets slot data from the API
    api
      .get<Slot[]>(`/v1/op/reservation-slots?venueId=&date=`)
      .then(() => {
        // Fallback: we cannot fetch by ID directly from list without venueId/date
        // The slot data should be passed via URL params or fetched differently
      })
      .catch(() => {});
  }, [id]);

  // Since we don't have a GET-by-ID endpoint for slots, use URL search params
  // from the referring page. For now, render a form that takes current values.
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const s = params.get("startTime");
    const e = params.get("endTime");
    const c = params.get("capacity");
    if (s) setStartTime(s);
    if (e) setEndTime(e);
    if (c) setCapacity(Number(c));
    if (s && e && c) {
      setSlot({ slotId: id, startTime: s, endTime: e, capacity: Number(c), approvedCount: 0 });
    }
  }, [id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await api.put(`/v1/op/reservation-slots/${id}`, {
        startTime,
        endTime,
        capacity,
      });
      router.push("/slots");
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError(t("blocked"));
      } else {
        setError(tc("error"));
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">{t("edit")}</h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label htmlFor="startTime" className="block text-sm font-medium mb-1">
              {t("startTime")}
            </label>
            <input
              id="startTime"
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              required
              className="w-full px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)]"
            />
          </div>
          <div>
            <label htmlFor="endTime" className="block text-sm font-medium mb-1">
              {t("endTime")}
            </label>
            <input
              id="endTime"
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              required
              className="w-full px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)]"
            />
          </div>
        </div>
        <div>
          <label htmlFor="capacity" className="block text-sm font-medium mb-1">
            {t("capacity")}
          </label>
          <input
            id="capacity"
            type="number"
            min="1"
            value={capacity}
            onChange={(e) => setCapacity(Number(e.target.value))}
            required
            className="w-full px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)]"
          />
        </div>
        {error && <p className="text-sm text-[var(--color-danger)]">{error}</p>}
        <div className="flex gap-3">
          <button
            type="submit"
            disabled={loading}
            className="px-4 py-2 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] hover:bg-[var(--color-primary-hover)] disabled:opacity-50 text-sm transition-colors"
          >
            {t("save")}
          </button>
          <button
            type="button"
            onClick={() => router.back()}
            className="px-4 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
          >
            {t("cancel")}
          </button>
        </div>
      </form>
    </div>
  );
}
