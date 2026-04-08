"use client";

import { AppleWalletButton } from "@/components/wallet/AppleWalletButton";
import { GoogleWalletButton } from "@/components/wallet/GoogleWalletButton";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

type TicketView = {
  ticketPassId: string;
  qrCodePayload: string;
  status: string;
  validFrom: string | null;
  validUntil: string | null;
};

const TOKYO_TZ = "Asia/Tokyo";

function formatTokyo(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat("ja-JP", {
    timeZone: TOKYO_TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(d);
}

type Phase = "before" | "active" | "expired";

function classifyValidity(now: number, validFrom: string | null, validUntil: string | null): Phase {
  if (validFrom) {
    const from = new Date(validFrom).getTime();
    if (Number.isFinite(from) && now < from) return "before";
  }
  if (validUntil) {
    const until = new Date(validUntil).getTime();
    if (Number.isFinite(until) && now >= until) return "expired";
  }
  return "active";
}

export function TicketCard({ ticket }: { ticket: TicketView }) {
  const t = useTranslations("wallet");
  const [dataUrl, setDataUrl] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>(() =>
    classifyValidity(Date.now(), ticket.validFrom, ticket.validUntil),
  );

  // Re-evaluate immediately when the ticket props change (prevents a
  // stale phase from a previous ticket leaking through React reconciliation)
  // and every minute so a ticket transitions from before → active →
  // expired without a manual refresh.
  useEffect(() => {
    setPhase(classifyValidity(Date.now(), ticket.validFrom, ticket.validUntil));
    const id = setInterval(() => {
      setPhase(classifyValidity(Date.now(), ticket.validFrom, ticket.validUntil));
    }, 60_000);
    return () => clearInterval(id);
  }, [ticket.validFrom, ticket.validUntil]);

  // Lazy-import qrcode (pure JS, ~10KB) only when the ticket is active.
  useEffect(() => {
    if (phase !== "active") {
      setDataUrl(null);
      return;
    }
    let cancelled = false;
    (async () => {
      const QR = await import("qrcode");
      const url = await QR.toDataURL(ticket.qrCodePayload, { margin: 1, width: 256 });
      if (!cancelled) setDataUrl(url);
    })();
    return () => {
      cancelled = true;
    };
  }, [phase, ticket.qrCodePayload]);

  return (
    <div className="rounded-[var(--radius-lg)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-sm)]">
      <div className="flex items-start justify-between gap-4">
        <div className="text-sm">
          <p className="font-semibold">チケット ID</p>
          <p className="font-mono text-xs text-[var(--color-ink-muted)]">{ticket.ticketPassId}</p>
          {ticket.validFrom && (
            <p className="mt-2">
              <span className="text-[var(--color-ink-muted)]">利用可能: </span>
              {formatTokyo(ticket.validFrom)}
              {ticket.validUntil ? ` 〜 ${formatTokyo(ticket.validUntil)}` : ""}
            </p>
          )}
        </div>
        <div className="shrink-0">
          {phase === "before" && (
            <p className="rounded-[var(--radius-sm)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-ink-muted)]">
              {formatTokyo(ticket.validFrom)} から利用可能
            </p>
          )}
          {phase === "expired" && (
            <p className="rounded-[var(--radius-sm)] bg-[var(--color-bg)] px-3 py-2 text-xs text-[var(--color-ink-muted)]">
              期限切れ
            </p>
          )}
          {phase === "active" && dataUrl && (
            <img
              src={dataUrl}
              alt={`QR code for ticket ${ticket.ticketPassId}`}
              width={256}
              height={256}
              className="rounded-[var(--radius-sm)] border border-[var(--color-border)]"
            />
          )}
          {phase === "active" && !dataUrl && (
            <p className="text-xs text-[var(--color-ink-muted)]">QRを生成中…</p>
          )}
        </div>
      </div>
      {phase !== "expired" && (
        <div className="mt-4 flex flex-wrap gap-3">
          <AppleWalletButton
            ticketId={ticket.ticketPassId}
            phase={phase}
            validFromLabel={formatTokyo(ticket.validFrom)}
            labels={{
              add: t("apple.add"),
              unavailableFrom: t("unavailableFrom"),
              downloading: t("apple.downloading"),
              error: t("error"),
            }}
          />
          <GoogleWalletButton
            ticketId={ticket.ticketPassId}
            phase={phase}
            validFromLabel={formatTokyo(ticket.validFrom)}
            labels={{
              add: t("google.add"),
              unavailableFrom: t("unavailableFrom"),
              loading: t("google.loading"),
              error: t("error"),
            }}
          />
        </div>
      )}
    </div>
  );
}
