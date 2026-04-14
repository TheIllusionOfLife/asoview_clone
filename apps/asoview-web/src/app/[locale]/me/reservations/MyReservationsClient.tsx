"use client";

import { Link, useRouter } from "@/i18n/navigation";
import {
  ApiError,
  NetworkError,
  type ReservationResponse,
  type ReservationStatusType,
  SignInRedirect,
  listMyReservations,
} from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

const PAGE_SIZE = 10;

const STATUS_TONE: Record<ReservationStatusType, string> = {
  PENDING_APPROVAL: "bg-yellow-100 text-yellow-800",
  APPROVED: "bg-green-100 text-green-800",
  WAITLISTED: "bg-blue-100 text-blue-800",
  REJECTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-[var(--color-bg)] text-[var(--color-ink-muted)]",
  COMPLETED: "bg-green-50 text-green-700",
};

function StatusBadge({
  status,
  label,
}: { status: ReservationStatusType; label: string }) {
  const tone = STATUS_TONE[status] ?? "bg-[var(--color-bg)]";
  return (
    <span
      className={`inline-block rounded-[var(--radius-sm)] px-2 py-0.5 text-xs font-semibold ${tone}`}
    >
      {label}
    </span>
  );
}

export function MyReservationsClient() {
  const t = useTranslations("reservations");
  const router = useRouter();
  const search = useSearchParams();
  const page = Math.max(0, Number.parseInt(search.get("page") ?? "0", 10) || 0);
  const { ready, user } = useAuth();
  const [reservations, setReservations] = useState<ReservationResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      router.push("/signin?next=/me/reservations");
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const list = await listMyReservations({
          signal: ctrl.signal,
          currentPath: "/me/reservations",
        });
        if (!cancelled) setReservations(list);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        setError(
          e instanceof ApiError || e instanceof NetworkError ? e.message : t("loadError"),
        );
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, router, t]);

  const pageSlice = useMemo(() => {
    if (!reservations) return [];
    return reservations.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  }, [reservations, page]);

  const totalPages = reservations
    ? Math.max(1, Math.ceil(reservations.length / PAGE_SIZE))
    : 1;

  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (!ready || reservations === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>;
  }
  if (reservations.length === 0) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("empty")}</p>;
  }
  return (
    <>
      <ul className="mt-6 space-y-3">
        {pageSlice.map((r) => (
          <li
            key={r.reservationId}
            className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-sm)]"
          >
            <Link
              href={`/me/reservations/${r.reservationId}`}
              className="block hover:opacity-90"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-medium">{r.guestName}</p>
                  <p className="mt-1 text-xs text-[var(--color-ink-muted)]">
                    {t("peopleSuffix", { n: r.guestCount })}
                  </p>
                  <p className="mt-1 font-mono text-xs text-[var(--color-ink-muted)]">
                    {r.reservationId}
                  </p>
                </div>
                <StatusBadge
                  status={r.status}
                  label={t(`statusLabels.${r.status}`)}
                />
              </div>
            </Link>
          </li>
        ))}
      </ul>
      {totalPages > 1 && (
        <nav className="mt-6 flex items-center justify-center gap-3 text-sm">
          {page > 0 && (
            <Link
              href={`/me/reservations?page=${page - 1}`}
              className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 hover:border-[var(--color-primary)]"
            >
              {t("prev")}
            </Link>
          )}
          <span className="text-[var(--color-ink-muted)]">
            {page + 1} / {totalPages}
          </span>
          {page + 1 < totalPages && (
            <Link
              href={`/me/reservations?page=${page + 1}`}
              className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 hover:border-[var(--color-primary)]"
            >
              {t("next")}
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
