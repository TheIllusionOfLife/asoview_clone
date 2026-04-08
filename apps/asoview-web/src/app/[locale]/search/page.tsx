import { SearchClient } from "./SearchClient";

/**
 * Shell-SSR search page. We render only the shell on the server and let
 * the client drive the URL-state roundtrip (facets, sort, autosuggest).
 * `force-dynamic` because every request carries a different query string
 * and we explicitly do NOT want to cache search results at the edge.
 */
export const dynamic = "force-dynamic";

type Multi = string | string[] | undefined;
type SearchParams = {
  q?: Multi;
  category?: Multi;
  priceMin?: Multi;
  priceMax?: Multi;
  sort?: Multi;
};

type Props = {
  searchParams: Promise<SearchParams>;
};

function firstParam(v: Multi): string | undefined {
  if (v === undefined) return undefined;
  return Array.isArray(v) ? v[0] : v;
}

export default async function SearchPage({ searchParams }: Props) {
  const sp = await searchParams;
  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <SearchClient
        initialQ={firstParam(sp.q) ?? ""}
        initialCategory={firstParam(sp.category) ?? ""}
        initialPriceMin={firstParam(sp.priceMin) ?? ""}
        initialPriceMax={firstParam(sp.priceMax) ?? ""}
        initialSort={firstParam(sp.sort) ?? "relevance"}
      />
    </div>
  );
}
