"use client";

import { Facets } from "@/components/search/Facets";
import { SearchBox } from "@/components/search/SearchBox";
import { SearchResults } from "@/components/search/SearchResults";
import { usePathname, useRouter } from "@/i18n/navigation";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect } from "react";

type Props = {
  initialQ: string;
  initialCategory: string;
  initialPriceMin: string;
  initialPriceMax: string;
  initialSort: string;
};

// Legacy hyphenated sort values emitted before the sort fix landed; any
// bookmarked / shared URL can still arrive with these. Normalize client-side
// so Facets sees a matching <option>, SearchResults sends the backend-
// canonical form to /v1/search, AND the URL gets rewritten so subsequent
// navigations carry the new form.
const SORT_ALIASES: Record<string, string> = {
  "price-asc": "price_asc",
  "price-desc": "price_desc",
};

/**
 * Client shell for /[locale]/search. Owns the single `updateParams`
 * callback that every child uses to mutate the URL. All facet/sort state
 * lives in the URL — children read from `useSearchParams` so back/forward
 * and refresh roundtrip for free.
 */
export function SearchClient(props: Props) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const updateParams = useCallback(
    (updates: Record<string, string | null>) => {
      const next = new URLSearchParams(searchParams?.toString() ?? "");
      for (const [key, value] of Object.entries(updates)) {
        if (value === null || value === "") {
          next.delete(key);
        } else {
          next.set(key, value);
        }
      }
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname);
    },
    [router, pathname, searchParams],
  );

  const rawSort = searchParams?.get("sort") ?? props.initialSort;
  const sort = SORT_ALIASES[rawSort] ?? rawSort;

  // If we normalized a legacy hyphenated value, rewrite the URL so the next
  // navigation / share carries the canonical form. Runs once on mount (and
  // if the raw sort ever changes to a hyphenated form again, e.g. via back
  // button after the rewrite).
  useEffect(() => {
    if (rawSort !== sort) {
      updateParams({ sort });
    }
  }, [rawSort, sort, updateParams]);

  const q = searchParams?.get("q") ?? props.initialQ;
  const category = searchParams?.get("category") ?? props.initialCategory;
  const priceMin = searchParams?.get("priceMin") ?? props.initialPriceMin;
  const priceMax = searchParams?.get("priceMax") ?? props.initialPriceMax;

  return (
    <div className="flex flex-col gap-6">
      <SearchBox value={q} onSubmit={(v) => updateParams({ q: v })} />
      <Facets
        category={category}
        priceMin={priceMin}
        priceMax={priceMax}
        sort={sort}
        onChange={updateParams}
      />
      <SearchResults
        q={q}
        category={category}
        priceMin={priceMin}
        priceMax={priceMax}
        sort={sort}
      />
    </div>
  );
}
