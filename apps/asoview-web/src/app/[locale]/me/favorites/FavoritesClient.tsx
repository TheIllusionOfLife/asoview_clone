"use client";

import { FavoriteToggle } from "@/components/favorites/FavoriteToggle";
import { Link } from "@/i18n/navigation";
import { useRouter } from "@/i18n/navigation";
import { ApiError, NetworkError, SignInRedirect, listFavorites } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

export function FavoritesClient() {
  const t = useTranslations("favorites");
  const router = useRouter();
  const { ready, user } = useAuth();
  const [ids, setIds] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready) return;
    if (!user) {
      router.push("/signin?next=/me/favorites");
      return;
    }
    let cancelled = false;
    const ctrl = new AbortController();
    (async () => {
      try {
        const list = await listFavorites({
          signal: ctrl.signal,
          currentPath: "/me/favorites",
        });
        if (!cancelled) setIds(list);
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
  }, [ready, user, router, t]);

  if (error) {
    return (
      <p role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {error}
      </p>
    );
  }
  if (!ready || ids === null) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("loading")}</p>;
  }
  if (ids.length === 0) {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">{t("empty")}</p>;
  }
  return (
    <ul className="mt-6 space-y-3">
      {ids.map((pid) => (
        <li
          key={pid}
          className="flex items-center justify-between rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
        >
          <Link
            href={`/products/${pid}`}
            className="font-mono text-xs text-[var(--color-ink-muted)] hover:text-[var(--color-primary)]"
          >
            {pid}
          </Link>
          <FavoriteToggle
            productId={pid}
            initialFavorited
            onChange={(next) => {
              if (!next) setIds((cur) => (cur ? cur.filter((x) => x !== pid) : cur));
            }}
          />
        </li>
      ))}
    </ul>
  );
}
