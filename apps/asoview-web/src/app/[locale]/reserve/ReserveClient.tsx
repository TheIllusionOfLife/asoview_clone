"use client";

import { useSearchParams } from "next/navigation";
import { useTranslations } from "next-intl";
import { ReservationForm } from "@/components/ReservationForm";

export function ReserveClient() {
  const t = useTranslations("reservationForm");
  const search = useSearchParams();
  const venueId = search.get("venueId");

  if (!venueId) {
    return <p className="text-sm text-[var(--color-ink-muted)]">{t("missingVenueId")}</p>;
  }

  return <ReservationForm venueId={venueId} />;
}
