import { setRequestLocale } from "next-intl/server";

export const dynamic = "force-static";

export function generateStaticParams() {
  return [{ locale: "ja" }, { locale: "en" }];
}

type Props = { params: Promise<{ locale: string }> };

// Pre-cached by the service worker on install, served as a fallback
// when a navigation-mode fetch fails. The Retry control MUST work
// without React hydration: when the SW serves this page offline, the
// `_next/static` JS chunks are not guaranteed to be cached, so any
// `onClick` handler tied to a "use client" component is a dead control.
//
// We use a plain `<form method="get">` with no `action` attribute. The
// browser submits the form to the current document URL (`document.URL`),
// which — because the SW responded to the original navigation — is still
// the URL the user was trying to reach. Submitting re-triggers the SW's
// navigate handler: online → real page loads; still offline → the same
// /offline shell renders again.
export default async function OfflinePage({ params }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const isJa = locale === "ja";
  const title = isJa ? "オフラインです" : "You are offline";
  const message = isJa
    ? "インターネットに接続できません。接続を確認してからもう一度お試しください。"
    : "We can't reach the network right now. Check your connection and try again.";
  const retry = isJa ? "再読み込み" : "Retry";
  return (
    <section className="mx-auto flex min-h-[60vh] max-w-xl flex-col items-center justify-center px-6 py-16 text-center">
      <h1 className="mb-4 font-[var(--font-fraunces)] text-3xl font-bold text-[var(--color-primary)]">
        {title}
      </h1>
      <p className="mb-8 text-base leading-relaxed text-[color:var(--color-foreground,#1a2238)]">
        {message}
      </p>
      <form method="get">
        <button
          type="submit"
          className="min-h-[44px] min-w-[120px] rounded-[var(--radius-md)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white shadow-[var(--shadow-md)] transition hover:bg-[var(--color-primary-hover)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]"
        >
          {retry}
        </button>
      </form>
    </section>
  );
}
