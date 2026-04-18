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

// Must match the <option value=...> emitted by Facets.tsx AND the
// case-statement in search-service VertexAiSearchQueryService.applySort.
// Underscored form is the backend-canonical one (Vertex AI Search rejects
// the bare field name for orderBy; the service prepends `structData.`).
// A mismatch here silently collapses deep-linked sort to "relevance".
const ALLOWED_SORTS = ["relevance", "price_asc", "price_desc"] as const;

// Backward-compat: hyphenated values were emitted by the frontend before
// this PR and are probably bookmarked / shared in the wild. Normalize them
// to the canonical underscored form so old URLs still sort as the user
// intended instead of silently falling back to relevance.
const SORT_ALIASES: Record<string, (typeof ALLOWED_SORTS)[number]> = {
  "price-asc": "price_asc",
  "price-desc": "price_desc",
};

function firstParam(v: Multi): string | undefined {
  if (v === undefined) return undefined;
  return Array.isArray(v) ? v[0] : v;
}

export default async function SearchPage({ searchParams }: Props) {
  const sp = await searchParams;
  const rawSort = firstParam(sp.sort);
  const normalizedSort = rawSort && SORT_ALIASES[rawSort] ? SORT_ALIASES[rawSort] : rawSort;
  const validatedSort = ALLOWED_SORTS.includes(normalizedSort as (typeof ALLOWED_SORTS)[number])
    ? (normalizedSort as string)
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
