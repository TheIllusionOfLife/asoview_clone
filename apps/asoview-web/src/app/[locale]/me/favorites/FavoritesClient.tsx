"use client";

import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState, useSyncExternalStore } from "react";
import { ProductCard } from "@/components/ProductCard";
import { useRouter } from "@/i18n/navigation";
import { ApiError, apiRequest, listFavorites, NetworkError, SignInRedirect } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import {
  getFavoritesSnapshot,
  seedFavoritesCache,
  subscribeFavorites,
} from "@/lib/favorites-cache";
import type { ProductResponse } from "@/lib/types";

export function FavoritesClient() {
  const t = useTranslations("favorites");
  const locale = useLocale();
  const router = useRouter();
  const { ready, user } = useAuth();
  const [products, setProducts] = useState<ProductResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Subscribe to the shared cache so un-favoriting a card via its heart
  // toggle re-renders the grid. `getFavoritesSnapshot` returns the live
  // Set (reference changes on every mutation) or a stable empty sentinel
  // while the cache is idle/loading, so initial mounts do not flap.
  const favoriteIds = useSyncExternalStore(
    subscribeFavorites,
    getFavoritesSnapshot,
    getFavoritesSnapshot,
  );

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      router.push("/signin?next=/me/favorites");
      return;
    }
    setProducts(null);
    setError(null);
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        // Fetch IDs directly (not via ensureFavoritesLoaded) — the cache
        // wrapper swallows SignInRedirect + ApiError inside a catch {} and
        // collapses them into state="error", which would hide a 401 from
        // this page's auth-aware error handling. Seed the shared cache
        // manually on success so sibling FavoriteToggle heart-states stay
        // in sync with this page.
        const ids = await listFavorites({
          signal: ctrl.signal,
          currentPath: "/me/favorites",
        });
        if (cancelled) return;
        seedFavoritesCache(ids);
        if (ids.length === 0) {
          setProducts([]);
          return;
        }
        const settled = await Promise.allSettled(
          ids.map((id) =>
            apiRequest<ProductResponse>(
              `/v1/products/${encodeURIComponent(id)}?lang=${encodeURIComponent(locale)}`,
              { signal: ctrl.signal, method: "GET", currentPath: "/me/favorites" },
            ),
          ),
        );
        if (cancelled) return;
        // If ANY product fetch returned a 401, the user's token expired
        // between the favorites-list fetch and the product-detail fetches.
        // Redirect to sign-in rather than silently showing a short grid.
        for (const r of settled) {
          if (r.status === "rejected" && r.reason instanceof SignInRedirect) {
            router.push(`/signin?next=${encodeURIComponent(r.reason.next)}`);
            return;
          }
        }
        const hydrated: ProductResponse[] = [];
        for (const r of settled) {
          if (r.status === "fulfilled") {
            hydrated.push(r.value);
          } else if (process.env.NODE_ENV !== "production") {
            console.warn("Favorites: dropping unfetched product", r.reason);
          }
        }
        setProducts(hydrated);
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        setError(e instanceof ApiError || e instanceof NetworkError ? e.message : t("loadError"));
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, user, router, t, locale]);

  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (!ready || products === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>;
  }
  // Filter against the live cache so toggling a heart on a ProductCard
  // drops the card on the next render, without re-fetching.
  const visible = products.filter((p) => favoriteIds.has(p.id));
  if (visible.length === 0) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("empty")}</p>;
  }
  return (
    <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
      {visible.map((p) => (
        <ProductCard key={p.id} product={p} />
      ))}
    </div>
  );
}
