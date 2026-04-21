"use client";

import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState, useSyncExternalStore } from "react";
import { ProductCard } from "@/components/ProductCard";
import { useRouter } from "@/i18n/navigation";
import { ApiError, apiRequest, listFavorites, NetworkError, SignInRedirect } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { isFavorited, subscribeFavorites } from "@/lib/favorites-cache";
import type { ProductResponse } from "@/lib/types";

export function FavoritesClient() {
  const t = useTranslations("favorites");
  const locale = useLocale();
  const router = useRouter();
  const { ready, user } = useAuth();
  const [products, setProducts] = useState<ProductResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    setProducts(null);
    setError(null);
    if (!user) {
      router.push("/signin?next=/me/favorites");
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const ids = await listFavorites({
          signal: ctrl.signal,
          currentPath: "/me/favorites",
        });
        if (cancelled) return;
        // Hydrate each ID into a full product. Per-card fetch failures
        // are silently dropped — a partial grid reads better than mixed
        // error tiles on a favorites page.
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
        for (let i = 0; i < settled.length; i++) {
          const r = settled[i];
          if (r.status === "fulfilled") hydrated.push(r.value);
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

  // Re-render when the favorites cache changes (e.g. user clicks the heart
  // on a ProductCard). We can't mutate `products` directly inside the
  // toggle, so this subscription drives the derived visible set instead.
  useSyncExternalStore(
    subscribeFavorites,
    () => "",
    () => "",
  );

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
  const visible = products.filter((p) => isFavorited(p.id));
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
