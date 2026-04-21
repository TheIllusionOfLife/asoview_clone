"use client";

import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState, useSyncExternalStore } from "react";
import { ProductCard } from "@/components/ProductCard";
import { useRouter } from "@/i18n/navigation";
import { ApiError, apiRequest, NetworkError, SignInRedirect } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import {
  ensureFavoritesLoaded,
  getFavoritesSnapshot,
  getFavoritesStatus,
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

  // Subscribe to the shared favorites cache so un-favoriting a card via its
  // heart toggle re-renders the grid. `getFavoritesSnapshot` returns the
  // Set-by-reference which changes on every mutation; a stable empty
  // sentinel is returned while the cache is idle/loading so initial mounts
  // do not flap.
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
        // Single authority: the favorites cache. Call ensureFavoritesLoaded
        // instead of listFavorites() directly so the cache transitions to
        // "ready" — otherwise isFavorited() (used to filter the grid) would
        // return false for every id and the page would show the empty
        // state despite the fetch succeeding.
        await ensureFavoritesLoaded();
        if (cancelled) return;
        if (getFavoritesStatus() === "error") {
          setError(t("loadError"));
          return;
        }
        const ids = Array.from(getFavoritesSnapshot());
        if (ids.length === 0) {
          setProducts([]);
          return;
        }
        const settled = await Promise.allSettled(
          ids.map((id) =>
            apiRequest<ProductResponse>(
              `/v1/products/${encodeURIComponent(id)}?lang=${encodeURIComponent(locale)}`,
              { signal: ctrl.signal, method: "GET" },
            ),
          ),
        );
        if (cancelled) return;
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
