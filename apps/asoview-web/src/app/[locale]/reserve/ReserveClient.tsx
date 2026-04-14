"use client";

import { ReservationForm } from "@/components/ReservationForm";
import { useSearchParams } from "next/navigation";

export function ReserveClient() {
  const search = useSearchParams();
  const venueId = search.get("venueId");

  if (!venueId) {
    return (
      <p className="text-sm text-[var(--color-ink-muted)]">
        venueId パラメータが必要です。
      </p>
    );
  }

  return <ReservationForm venueId={venueId} />;
}
