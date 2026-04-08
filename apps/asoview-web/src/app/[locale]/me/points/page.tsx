import { getTranslations } from "next-intl/server";
import { PointsClient } from "./PointsClient";

// Auth-gated client fetch; no static prerender.
export const dynamic = "force-dynamic";

export default async function PointsPage() {
  const t = await getTranslations("points");
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">{t("pageTitle")}</h1>
      <PointsClient />
    </div>
  );
}
