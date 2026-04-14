"use client";

import { api } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

type Props = {
  value: string;
  onChange: (venueId: string) => void;
};

export function VenueSelector({ value, onChange }: Props) {
  const t = useTranslations("dashboard");
  const tc = useTranslations("common");
  const [venues, setVenues] = useState<string[]>([]);
  const [error, setError] = useState(false);

  useEffect(() => {
    api
      .get<string[]>("/v1/op/me/venues")
      .then(setVenues)
      .catch(() => setError(true));
  }, []);

  return (
    <div>
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-label={t("selectVenue")}
        className="px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
      >
        <option value="">{t("selectVenue")}</option>
        {venues.map((v) => (
          <option key={v} value={v}>
            {v}
          </option>
        ))}
      </select>
      {error && <p className="text-xs text-[var(--color-danger)] mt-1">{tc("error")}</p>}
    </div>
  );
}
