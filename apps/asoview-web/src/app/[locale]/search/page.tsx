import { SearchClient } from "./SearchClient";

/**
 * Shell-SSR search page. We render only the shell on the server and let
 * the client drive the URL-state roundtrip (facets, sort, autosuggest).
 * `force-dynamic` because every request carries a different query string
 * and we explicitly do NOT want to cache search results at the edge.
 */
export const dynamic = "force-dynamic";

type Multi = string | string[] | undefined;
interface SearchParams {
  q?: Multi;
  category?: Multi;
  priceMin?: Multi;
  priceMax?: Multi;
  sort?: Multi;
}

interface Props {
  searchParams: Promise<SearchParams>;
}

const ALLOWED_SORTS = ["relevance", "priceAsc", "priceDesc"] as const;

function firstParam(v: Multi): string | undefined {
  if (v === undefined) return undefined;
  return Array.isArray(v) ? v[0] : v;
}

export default async function SearchPage({ searchParams }: Props) {
  const sp = await searchParams;
  const rawSort = firstParam(sp.sort);
  const validatedSort = ALLOWED_SORTS.includes(rawSort as (typeof ALLOWED_SORTS)[number])
    ? (rawSort as string)
    : "relevance";
  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <SearchClient
        initialQ={firstParam(sp.q) ?? ""}
        initialCategory={firstParam(sp.category) ?? ""}
        initialPriceMin={firstParam(sp.priceMin) ?? ""}
        initialPriceMax={firstParam(sp.priceMax) ?? ""}
        initialSort={validatedSort}
      />
    </div>
  );
}
