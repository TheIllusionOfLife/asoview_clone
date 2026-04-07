import { SearchClient } from "./SearchClient";

/**
 * Shell-SSR search page. We render only the shell on the server and let
 * the client drive the URL-state roundtrip (facets, sort, autosuggest).
 * `force-dynamic` because every request carries a different query string
 * and we explicitly do NOT want to cache search results at the edge.
 */
export const dynamic = "force-dynamic";

type SearchParams = {
  q?: string;
  category?: string;
  priceMin?: string;
  priceMax?: string;
  sort?: string;
};

type Props = {
  searchParams: Promise<SearchParams>;
};

export default async function SearchPage({ searchParams }: Props) {
  const sp = await searchParams;
  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <SearchClient
        initialQ={sp.q ?? ""}
        initialCategory={sp.category ?? ""}
        initialPriceMin={sp.priceMin ?? ""}
        initialPriceMax={sp.priceMax ?? ""}
        initialSort={sp.sort ?? "relevance"}
      />
    </div>
  );
}
