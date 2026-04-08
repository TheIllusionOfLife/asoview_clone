"use client";

import { useRouter } from "@/i18n/navigation";
import { ApiError, NetworkError, SignInRedirect } from "@/lib/api";
import { getGoogleWalletUrl } from "@/lib/wallet";
import { useState } from "react";

type Phase = "before" | "active" | "expired";

type Props = {
  ticketId: string;
  phase: Phase;
  validFromLabel?: string;
  labels: {
    add: string;
    unavailableFrom: string;
    loading: string;
    error: string;
  };
};

/**
 * Google Wallet button. Fetches the save URL
 * (GET /v1/me/tickets/{ticketId}/google-pass-link -> {saveUrl}) and
 * opens it in a new tab.
 */
export function GoogleWalletButton({ ticketId, phase, validFromLabel, labels }: Props) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (phase === "expired") return null;

  const disabled = phase !== "active" || busy;

  async function onClick() {
    if (disabled) return;
    setBusy(true);
    setError(null);
    try {
      const { saveUrl } = await getGoogleWalletUrl(ticketId);
      window.open(saveUrl, "_blank", "noopener,noreferrer");
    } catch (e) {
      if (e instanceof SignInRedirect) {
        // e.next is already locale-stripped; the router from @/i18n/navigation
        // will re-prefix the active locale.
        router.push(`/signin?next=${encodeURIComponent(e.next)}`);
        return;
      }
      if (e instanceof ApiError || e instanceof NetworkError) {
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
        className="inline-flex items-center gap-2 rounded-[var(--radius-sm)] bg-[var(--color-primary)] px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
      >
        {busy ? labels.loading : labels.add}
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
