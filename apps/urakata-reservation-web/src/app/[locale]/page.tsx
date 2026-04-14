"use client";

import { useTranslations } from "next-intl";

export default function DashboardRedirect() {
  const t = useTranslations("dashboard");
  return (
    <div>
      <h1 className="text-2xl font-bold mb-4">{t("title")}</h1>
      <p className="text-[var(--color-text-muted)]">
        {t("selectVenue")}
      </p>
    </div>
  );
}
