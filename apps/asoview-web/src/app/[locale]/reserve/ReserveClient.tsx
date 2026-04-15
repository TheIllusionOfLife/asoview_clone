"use client";

import { ReservationForm } from "@/components/ReservationForm";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";

export function ReserveClient() {
  const t = useTranslations("reservationForm");
  const search = useSearchParams();
  const venueId = search.get("venueId");

  if (!venueId) {
    return <p className="text-sm text-[var(--color-ink-muted)]">{t("missingVenueId")}</p>;
  }

  return <ReservationForm venueId={venueId} />;
}
