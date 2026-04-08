"use client";

import { addFavorite, removeFavorite } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import {
  ensureFavoritesLoaded,
  isFavorited,
  markFavorited,
  markUnfavorited,
  subscribeFavorites,
} from "@/lib/favorites-cache";
import { useTranslations } from "next-intl";
import { useEffect, useState, useSyncExternalStore } from "react";

/**
 * Heart toggle. Reads its initial state from the singleton favorites
 * cache (`src/lib/favorites-cache.ts`) which fetches `GET /v1/me/favorites`
 * exactly once and shares the result across all toggles on the page.
 *
 * Optimistic local state, reverts on API failure. The cache is updated
 * on success so other toggles for the same product re-render in sync.
 */
export function FavoriteToggle({
  productId,
  initialFavorited,
  onChange,
}: {
  productId: string;
  /** When provided, skips the cache fetch and seeds local state directly. */
  initialFavorited?: boolean;
  onChange?: (next: boolean) => void;
}) {
  const t = useTranslations("favorites");
  const { user, ready } = useAuth();
  const cached = useSyncExternalStore(
    subscribeFavorites,
    () => isFavorited(productId),
    () => false,
  );
  // Local override lets the component reflect optimistic state even
  // before the cache has loaded. Resets back to the cache value via the
  // useEffect below whenever the cache changes underneath us.
  const [override, setOverride] = useState<boolean | null>(null);
  const favorited = override ?? initialFavorited ?? cached;
  const [pending, setPending] = useState(false);

  // biome-ignore lint/correctness/useExhaustiveDependencies: cached drives the reset
  useEffect(() => {
    setOverride(null);
  }, [cached]);

  // Trigger the cache fetch on mount when an authenticated user is present
  // and the parent did not pre-seed initial state. The ensure call is
  // idempotent and shares an in-flight promise across mounts.
  useEffect(() => {
    if (initialFavorited !== undefined) return;
    if (!ready || !user) return;
    let cancelled = false;
    void ensureFavoritesLoaded().then(() => {
      if (cancelled) return;
    });
    return () => {
      cancelled = true;
    };
  }, [initialFavorited, ready, user]);

  const disabled = pending || (ready && !user);

  async function toggle(e: React.MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (disabled) return;
    const next = !favorited;
    setOverride(next);
    if (next) markFavorited(productId);
    else markUnfavorited(productId);
    setPending(true);
    try {
      if (next) await addFavorite(productId);
      else await removeFavorite(productId);
      onChange?.(next);
    } catch {
      setOverride(!next);
      if (next) markUnfavorited(productId);
      else markFavorited(productId);
    } finally {
      setPending(false);
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      disabled={disabled}
      aria-pressed={favorited}
      aria-label={favorited ? t("ariaRemove") : t("ariaAdd")}
      className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] text-lg hover:border-[var(--color-primary)] disabled:opacity-60"
    >
      <span aria-hidden="true">{favorited ? "♥" : "♡"}</span>
    </button>
  );
}
