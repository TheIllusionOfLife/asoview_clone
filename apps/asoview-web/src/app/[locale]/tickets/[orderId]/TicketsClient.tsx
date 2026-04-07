"use client";

import { Link } from "@/i18n/navigation";
import { useRouter } from "@/i18n/navigation";
import { ApiError, NetworkError, SignInRedirect, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useEffect, useState } from "react";
import { TicketCard } from "./TicketCard";

type TicketView = {
  ticketPassId: string;
  entitlementId: string;
  orderId: string;
  qrCodePayload: string;
  status: string;
  /** ISO instant in Asia/Tokyo (e.g. "2026-04-12T10:00:00+09:00"). */
  validFrom: string | null;
  validUntil: string | null;
};

export function TicketsClient({ orderId }: { orderId: string }) {
  const router = useRouter();
  const { ready } = useAuth();
  const [tickets, setTickets] = useState<TicketView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    if (!ready) return;
    // Reset transient UI state so a re-fetch (e.g. orderId change or
    // auth-tick) doesn't render stale notFound/error from a previous
    // fetch while the new one is in flight.
    setNotFound(false);
    setError(null);
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const list = await api.get<TicketView[]>(
          `/v1/me/tickets?orderId=${encodeURIComponent(orderId)}`,
          { signal: ctrl.signal, currentPath: `/tickets/${orderId}` },
        );
        if (cancelled) return;
        // Empty list = cross-user OR no tickets minted yet. Either way,
        // render the "not found" UX so we don't leak the existence of
        // foreign orders.
        if (list.length === 0) {
          setNotFound(true);
          return;
        }
        setTickets(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        if (e instanceof ApiError && e.status === 404) {
          setNotFound(true);
          return;
        }
        setError(
          e instanceof ApiError || e instanceof NetworkError
            ? e.message
            : "チケットの取得中にエラーが発生しました",
        );
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, orderId, router]);

  if (notFound) {
    return (
      <div className="mt-6">
        <p className="text-sm">このチケットは見つかりませんでした。</p>
        <Link href="/me/orders" className="mt-3 inline-block text-sm text-[var(--color-primary)]">
          予約履歴へ
        </Link>
      </div>
    );
  }
  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (tickets === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>;
  }
  return (
    <ul className="mt-6 space-y-4">
      {tickets.map((t) => (
        <li key={t.ticketPassId}>
          <TicketCard ticket={t} />
        </li>
      ))}
    </ul>
  );
}
