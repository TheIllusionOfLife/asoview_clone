import { setRequestLocale } from "next-intl/server";
import { RetryButtonClient } from "./RetryButtonClient";

export const dynamic = "force-static";

export function generateStaticParams() {
  return [{ locale: "ja" }, { locale: "en" }];
}

type Props = { params: Promise<{ locale: string }> };

// Pre-cached by the service worker on install, served as a fallback when
// a navigation-mode fetch fails. Kept deliberately minimal: no translation
// catalog loads, no data fetches, no auth checks. The Retry button reloads
// the current URL so the user lands on the live page once connectivity
// returns.
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
      <RetryButtonClient label={retry} />
    </section>
  );
}
