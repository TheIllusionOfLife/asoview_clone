"use client";

import { Link } from "@/i18n/navigation";
import { type ProductSearchResponse, searchProducts } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

type Props = {
  q: string;
  category: string;
  priceMin: string;
  priceMax: string;
  sort: string;
};

/**
 * Fetches `/v1/search` with the current facets on every change. Shows
 * a skeleton while loading and a translated empty state otherwise.
 */
export function SearchResults(props: Props) {
  const t = useTranslations("search");
  const [state, setState] = useState<
    | { kind: "idle" }
    | { kind: "loading" }
    | { kind: "ok"; data: ProductSearchResponse }
    | { kind: "error" }
  >({ kind: "idle" });

  useEffect(() => {
    // money-parse-ok: integer yen bounds only
    const minNum = props.priceMin ? Number.parseInt(props.priceMin, 10) : undefined;
    const maxNum = props.priceMax ? Number.parseInt(props.priceMax, 10) : undefined;
    const controller = new AbortController();
    setState({ kind: "loading" });
    searchProducts(
      {
        q: props.q || undefined,
        category: props.category || undefined,
        priceMin: Number.isFinite(minNum) ? minNum : undefined,
        priceMax: Number.isFinite(maxNum) ? maxNum : undefined,
        sort: props.sort || undefined,
        page: 0,
        size: 20,
      },
      { signal: controller.signal },
    )
      .then((data) => setState({ kind: "ok", data }))
      .catch((e) => {
        if (e?.name === "NetworkError" && controller.signal.aborted) return;
        setState({ kind: "error" });
      });
    return () => controller.abort();
  }, [props.q, props.category, props.priceMin, props.priceMax, props.sort]);

  if (state.kind === "loading" || state.kind === "idle") {
    return (
      <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4" aria-busy="true">
        {Array.from({ length: 6 }).map((_, i) => (
          <li
            // biome-ignore lint/suspicious/noArrayIndexKey: skeleton placeholder
            key={i}
            className="h-32 animate-pulse rounded-lg bg-[var(--color-surface-hover)]"
          />
        ))}
      </ul>
    );
  }

  if (state.kind === "error") {
    return <p className="text-sm text-red-600">{t("error")}</p>;
  }

  const hits = state.data.content ?? [];
  if (hits.length === 0) {
    return <p className="text-[var(--color-ink-muted)]">{t("noResults", { q: props.q || "" })}</p>;
  }

  return (
    <ul className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {hits.map((hit) => (
        <li
          key={hit.productId}
          className="rounded-lg border border-[var(--color-border)] p-4 hover:shadow-md"
        >
          <Link href={`/products/${hit.productId}`} className="block">
            <h3 className="font-semibold">{hit.name}</h3>
            {hit.description && (
              <p className="mt-1 text-sm text-[var(--color-ink-muted)] line-clamp-2">
                {hit.description}
              </p>
            )}
            {hit.minPrice != null && (
              <p className="mt-2 text-sm">
                {new Intl.NumberFormat("ja-JP", {
                  style: "currency",
                  currency: "JPY",
                  maximumFractionDigits: 0,
                }).format(hit.minPrice)}
                {" ~"}
              </p>
            )}
          </Link>
        </li>
      ))}
    </ul>
  );
}
