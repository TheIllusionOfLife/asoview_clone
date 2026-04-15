"use client";

import { Link, useRouter } from "@/i18n/navigation";
import { ApiError, type TicketPass, listMyTickets } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { statusBadgeClass, statusLabel, statusOrder } from "@/lib/ticketStatus";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";

export default function TicketListPage() {
  const t = useTranslations("tickets");
  const ta = useTranslations("app");
  const tc = useTranslations("common");
  const locale = useLocale();
  const { ready, user, signOut } = useAuth();
  const router = useRouter();
  const [tickets, setTickets] = useState<TicketPass[] | null>(null);
  // Start at true to avoid a flash of "no tickets" before the first fetch
  // resolves — initial mount always triggers a fetch.
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const fetchTickets = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const data = await listMyTickets();
      // Sort VALID first; inside each group newest (by validFrom desc) at top.
      data.sort((a, b) => {
        const s = statusOrder(a.status) - statusOrder(b.status);
        if (s !== 0) return s;
        return (b.validFrom ?? "").localeCompare(a.validFrom ?? "");
      });
      setTickets(data);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        router.replace("/login");
        return;
      }
      console.error("Failed to load ticket list", e);
      setTickets(null);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    if (ready && user) fetchTickets();
  }, [ready, user, fetchTickets]);

  return (
    <div className="max-w-2xl mx-auto p-4 sm:p-6">
      <header className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t("title")}</h1>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={fetchTickets}
            className="text-sm text-[var(--color-text-muted)] hover:text-[var(--color-primary)]"
          >
            {t("refresh")}
          </button>
          <button
            type="button"
            onClick={() => signOut()}
            className="text-sm text-[var(--color-text-muted)] hover:text-[var(--color-danger)]"
          >
            {ta("logout")}
          </button>
        </div>
      </header>

      {loading && <p className="text-[var(--color-text-muted)]">{tc("loading")}</p>}

      {error && (
        <div className="text-center py-6">
          <p className="text-[var(--color-danger)] mb-3">{tc("error")}</p>
          <button
            type="button"
            onClick={fetchTickets}
            className="px-4 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
          >
            {tc("retry")}
          </button>
        </div>
      )}

      {!loading && !error && tickets && tickets.length === 0 && (
        <p className="text-[var(--color-text-muted)]">{t("empty")}</p>
      )}

      {!loading && !error && tickets && tickets.length > 0 && (
        <ul className="space-y-3">
          {tickets.map((tk) => (
            <li key={tk.ticketPassId}>
              <Link
                href={`/tickets/${tk.ticketPassId}`}
                className="block bg-white rounded-[var(--radius-lg)] border border-[var(--color-border)] p-4 hover:border-[var(--color-primary)] transition-colors"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 min-w-0">
                    {/* Intentionally do NOT show tk.qrCodePayload here. The payload is the
                        redeemable secret; exposing it on the list card invites shoulder-surfing
                        on a shared screen. Users see the QR only after tapping into the detail
                        view, which is an explicit "show at gate" intent. */}
                    <p className="text-sm font-medium">{t("title")}</p>
                    <p className="text-xs text-[var(--color-text-muted)] mt-1">
                      {t("orderId")}: {tk.orderId.slice(0, 8)}...
                    </p>
                    {tk.validUntil && (
                      <p className="text-xs text-[var(--color-text-muted)] mt-1">
                        {t("validUntil")}: {new Date(tk.validUntil).toLocaleString(locale)}
                      </p>
                    )}
                  </div>
                  <span
                    className={`text-xs font-semibold px-2 py-1 rounded ${statusBadgeClass(tk.status)}`}
                  >
                    {statusLabel(tk.status, t)}
                  </span>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
