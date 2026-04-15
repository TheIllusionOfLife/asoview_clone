import { getTranslations } from "next-intl/server";
import { Suspense } from "react";
import { MyReservationsClient } from "./MyReservationsClient";

export const dynamic = "force-dynamic";

export async function generateMetadata({ params }: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "reservations" });
  return { title: t("pageTitle") };
}

export default async function MyReservationsPage({
  params,
}: { params: Promise<{ locale: string }> }) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "reservations" });
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">{t("pageTitle")}</h1>
      <Suspense
        fallback={<p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>}
      >
        <MyReservationsClient />
      </Suspense>
    </div>
  );
}
