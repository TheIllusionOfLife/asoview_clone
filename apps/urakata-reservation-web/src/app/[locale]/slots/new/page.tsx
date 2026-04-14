"use client";

import { useTranslations } from "next-intl";
import { api } from "@/lib/api";
import { VenueSelector } from "@/components/VenueSelector";
import { useRouter } from "@/i18n/navigation";
import { useState } from "react";

export default function CreateSlotPage() {
  const t = useTranslations("slots");
  const router = useRouter();
  const [venueId, setVenueId] = useState("");
  const [productId, setProductId] = useState("");
  const [slotDate, setSlotDate] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [capacity, setCapacity] = useState(10);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (endTime <= startTime) {
      setError("End time must be after start time");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await api.post("/v1/op/reservation-slots", {
        venueId,
        productId,
        slotDate,
        startTime,
        endTime,
        capacity,
      });
      router.push("/slots");
    } catch {
      setError("Failed to create slot");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-lg">
      <h1 className="text-2xl font-bold mb-6">{t("create")}</h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">{t("venue")}</label>
          <VenueSelector value={venueId} onChange={setVenueId} />
        </div>
        <div>
          <label htmlFor="productId" className="block text-sm font-medium mb-1">
            {t("productId")}
          </label>
          <input
            id="productId"
            type="text"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
            required
            className="w-full px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)]"
          />
        </div>
        <div>
          <label htmlFor="slotDate" className="block text-sm font-medium mb-1">
            {t("date")}
          </label>
          <input
            id="slotDate"
            type="date"
            value={slotDate}
            onChange={(e) => setSlotDate(e.target.value)}
            required
            className="w-full px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)]"
          />
        </div>
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
