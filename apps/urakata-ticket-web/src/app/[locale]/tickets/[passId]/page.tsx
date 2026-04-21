"use client";

import { useParams } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import { QRCodeSVG } from "qrcode.react";
import { useCallback, useEffect, useState } from "react";
import { Link, useRouter } from "@/i18n/navigation";
import { ApiError, listMyTickets, type TicketPass } from "@/lib/api";
import { useAuth } from "@/lib/auth";

export default function TicketDetailPage() {
  const t = useTranslations("tickets");
  const tc = useTranslations("common");
  const locale = useLocale();
  const { ready, user } = useAuth();
  const router = useRouter();
  const params = useParams<{ passId: string }>();
  const passId = params.passId;
  const [ticket, setTicket] = useState<TicketPass | null>(null);
  // Start at true so the initial render shows "loading" instead of briefly
  // rendering the "no longer valid" card before the first fetch resolves.
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchTicket = useCallback(async () => {
    if (!passId) return;
    setLoading(true);
    setError(false);
    try {
      // Backend has /v1/me/tickets (list); filtering client-side keeps the API surface
      // minimal for MVP. The list is user-scoped, so this cannot leak other users' passes.
      const list = await listMyTickets();
      const found = list.find((x) => x.ticketPassId === passId) ?? null;
      setTicket(found);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        router.replace("/login");
        return;
      }
      console.error("Failed to load ticket detail", e);
      setTicket(null);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [passId, router]);

  useEffect(() => {
    if (ready && user) fetchTicket();
  }, [ready, user, fetchTicket]);

  // Refetch on visibility change — picks up scanner-side redemption or server-side revoke.
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === "visible") fetchTicket();
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, [fetchTicket]);

  return (
    <div className="max-w-md mx-auto p-4 sm:p-6">
      <header className="mb-4">
        <Link
          href="/"
          className="text-sm text-[var(--color-text-muted)] hover:text-[var(--color-primary)]"
        >
          ← {tc("back")}
        </Link>
      </header>

      {loading && <p className="text-[var(--color-text-muted)]">{tc("loading")}</p>}

      {error && (
        <div className="text-center py-6">
          <p className="text-[var(--color-danger)] mb-3">{tc("error")}</p>
          <button
            type="button"
            onClick={fetchTicket}
            className="px-4 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
          >
            {tc("retry")}
          </button>
        </div>
      )}

      {!loading && !error && ticket === null && (
        <p className="text-[var(--color-text-muted)]">{t("noLongerValid")}</p>
      )}

      {!loading && !error && ticket && (
        <div className="bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-6">
          {ticket.status === "VALID" ? (
            <>
              <p className="text-center text-sm text-[var(--color-text-muted)] mb-4">
                {t("showAtGate")}
              </p>
              <div className="flex justify-center mb-4 bg-white p-4">
                {/* The payload IS the redeemable secret. Do not echo it in aria-label or
                    in a <p> — use a generic accessible label and keep the raw value only
                    in the SVG's encoded content (where sighted users scan it with a camera). */}
                <QRCodeSVG
                  value={ticket.qrCodePayload}
                  size={256}
                  level="M"
                  aria-label={t("qrAccessibleLabel")}
                />
              </div>
            </>
          ) : (
            <div className="py-8 text-center">
              <p className="text-lg font-bold text-[var(--color-danger)] mb-2">
                {t("noLongerValid")}
              </p>
              <p className="text-sm text-[var(--color-text-muted)]">
                {ticket.status === "USED" && t("used")}
                {ticket.status === "EXPIRED" && t("expired")}
                {ticket.status === "REVOKED" && t("revoked")}
              </p>
            </div>
          )}

          <dl className="mt-6 space-y-2 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-[var(--color-text-muted)]">{t("orderId")}</dt>
              <dd className="font-mono text-xs break-all text-right">{ticket.orderId}</dd>
            </div>
            {ticket.validFrom && (
              <div className="flex justify-between gap-4">
                <dt className="text-[var(--color-text-muted)]">{t("validFrom")}</dt>
                <dd>{new Date(ticket.validFrom).toLocaleString(locale)}</dd>
              </div>
            )}
            {ticket.validUntil && (
              <div className="flex justify-between gap-4">
                <dt className="text-[var(--color-text-muted)]">{t("validUntil")}</dt>
                <dd>{new Date(ticket.validUntil).toLocaleString(locale)}</dd>
              </div>
            )}
          </dl>
        </div>
      )}
    </div>
  );
}
