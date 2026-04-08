import { getTranslations } from "next-intl/server";
import { FavoritesClient } from "./FavoritesClient";

// Auth-gated client fetch; no static prerender.
export const dynamic = "force-dynamic";

export default async function FavoritesPage() {
  const t = await getTranslations("favorites");
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">{t("title")}</h1>
      <FavoritesClient />
    </div>
  );
}
