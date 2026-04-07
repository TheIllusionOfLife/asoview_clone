import { routing } from "@/i18n/routing";
import { NextIntlClientProvider, hasLocale } from "next-intl";
import { getMessages, setRequestLocale } from "next-intl/server";
/**
 * Locale-scoped layout. Validates the locale segment, pins the active
 * locale into next-intl's request context (so server components can
 * read it synchronously via `useTranslations`), and hands the messages
 * to `NextIntlClientProvider` for client components further down the
 * tree. Rendering 404 for unknown locales keeps enumeration of junk
 * `/zz/...` URLs out of SSR caches.
 */
import { notFound } from "next/navigation";

export function generateStaticParams() {
  return routing.locales.map((locale) => ({ locale }));
}

type Props = {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
};

export default async function LocaleLayout({ children, params }: Props) {
  const { locale } = await params;
  if (!hasLocale(routing.locales, locale)) {
    notFound();
  }
  setRequestLocale(locale);
  const messages = await getMessages();
  return <NextIntlClientProvider messages={messages}>{children}</NextIntlClientProvider>;
}
