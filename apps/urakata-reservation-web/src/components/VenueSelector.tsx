"use client";

import { useTranslations } from "next-intl";
import { api } from "@/lib/api";
import { useEffect, useState } from "react";

type Props = {
  value: string;
  onChange: (venueId: string) => void;
};

export function VenueSelector({ value, onChange }: Props) {
  const t = useTranslations("dashboard");
  const [venues, setVenues] = useState<string[]>([]);

  useEffect(() => {
    api.get<string[]>("/v1/op/me/venues").then(setVenues).catch(() => {});
  }, []);

  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
    >
      <option value="">{t("selectVenue")}</option>
      {venues.map((v) => (
        <option key={v} value={v}>
          {v}
        </option>
      ))}
    </select>
  );
}
