"use client";

import { voteHelpful } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useState } from "react";

/**
 * Optimistic helpful-vote toggle. Backend has no dedup: a second click
 * is a no-op on the client side (local `voted` flag). The button is
 * disabled after the first successful call to prevent duplicate POSTs.
 */
export function HelpfulButton({
  reviewId,
  initialCount,
}: {
  reviewId: string;
  initialCount: number;
}) {
  const t = useTranslations("reviews");
  const [count, setCount] = useState(initialCount);
  const [voted, setVoted] = useState(false);
  const [pending, setPending] = useState(false);

  async function onClick() {
    if (voted || pending) return;
    setPending(true);
    // Optimistic increment.
    setCount((c) => c + 1);
    setVoted(true);
    try {
      await voteHelpful(reviewId);
    } catch {
      // Revert on failure.
      setCount((c) => Math.max(0, c - 1));
      setVoted(false);
    } finally {
      setPending(false);
    }
  }

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={voted || pending}
      aria-pressed={voted}
      className="inline-flex items-center gap-1 rounded-[var(--radius-md)] border border-[var(--color-border)] px-2 py-1 text-xs hover:border-[var(--color-primary)] disabled:opacity-60"
    >
      <span aria-hidden="true">👍</span>
      <span>
        {t("helpful")} ({count})
      </span>
    </button>
  );
}
