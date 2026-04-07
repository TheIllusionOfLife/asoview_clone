"use client";

import { ApiError, NetworkError, SignInRedirect } from "@/lib/api";
import { downloadApplePass } from "@/lib/wallet";
import { useState } from "react";

type Phase = "before" | "active" | "expired";

type Props = {
  ticketId: string;
  phase: Phase;
  validFromLabel?: string;
  labels: {
    add: string;
    unavailableFrom: string; // e.g. "Available from {date}"
    downloading: string;
    error: string;
  };
};

/**
 * Apple Wallet button. Fetches the signed .pkpass from the gateway
 * (GET /v1/me/tickets/{ticketId}/apple-pass) and triggers a browser download.
 *
 * Validity gating:
 *  - before validFrom: disabled with "Available from ..." label
 *  - active:           enabled
 *  - expired:          not rendered (parent hides the strip)
 */
export function AppleWalletButton({ ticketId, phase, validFromLabel, labels }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (phase === "expired") return null;

  const disabled = phase !== "active" || busy;

  async function onClick() {
    if (disabled) return;
    setBusy(true);
    setError(null);
    try {
      const blob = await downloadApplePass(ticketId);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `ticket-${ticketId}.pkpass`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (e) {
      if (e instanceof SignInRedirect) {
        setError(labels.error);
      } else if (e instanceof ApiError || e instanceof NetworkError) {
        setError(e.message || labels.error);
      } else {
        setError(labels.error);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <button
        type="button"
        onClick={onClick}
        disabled={disabled}
        aria-disabled={disabled}
        className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] bg-black px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
      >
        {busy ? labels.downloading : labels.add}
      </button>
      {phase === "before" && validFromLabel && (
        <p className="mt-1 text-xs text-[var(--color-ink-muted)]">
          {labels.unavailableFrom.replace("{date}", validFromLabel)}
        </p>
      )}
      {error && (
        <p role="alert" className="mt-1 text-xs text-[var(--color-danger)]">
          {error}
        </p>
      )}
    </div>
  );
}
