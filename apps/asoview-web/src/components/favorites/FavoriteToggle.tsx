"use client";

import { addFavorite, removeFavorite } from "@/lib/api";
import { useState } from "react";

/**
 * Heart toggle. Optimistic local state, reverts on API failure.
 * Initial state is provided by the parent via `initialFavorited` (the
 * parent pre-fetches `GET /v1/me/favorites` and derives the map).
 */
export function FavoriteToggle({
  productId,
  initialFavorited = false,
  onChange,
}: {
  productId: string;
  initialFavorited?: boolean;
  onChange?: (next: boolean) => void;
}) {
  const [favorited, setFavorited] = useState(initialFavorited);
  const [pending, setPending] = useState(false);

  async function toggle(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (pending) return;
    const next = !favorited;
    setFavorited(next);
    setPending(true);
    try {
      if (next) await addFavorite(productId);
      else await removeFavorite(productId);
      onChange?.(next);
    } catch {
      // Revert.
      setFavorited(!next);
    } finally {
      setPending(false);
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={pending}
      aria-pressed={favorited}
      aria-label={favorited ? "お気に入り解除" : "お気に入り追加"}
      className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] text-lg hover:border-[var(--color-primary)] disabled:opacity-60"
    >
      <span aria-hidden="true">{favorited ? "♥" : "♡"}</span>
    </button>
  );
}
