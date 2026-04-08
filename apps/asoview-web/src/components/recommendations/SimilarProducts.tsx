import { ProductCard } from "@/components/ProductCard";
import { ServerFetchError, serverGet } from "@/lib/server-api";
import type { Page, ProductResponse } from "@/lib/types";

/**
 * Horizontal recommendation strip. Server component — fetches via
 * `GET /v1/products` (catalog list endpoint; already hard-filters
 * `status=ACTIVE` server-side). When `categoryId` is provided we scope
 * to that category ("similar experiences"); otherwise we fall back to
 * the newest/featured list ("popular experiences").
 *
 * `excludeProductId` filters the current product out of the results so
 * the similar-experiences strip on a product detail page never shows
 * the product the user is already looking at.
 */
export async function SimilarProducts({
  title,
  categoryId,
  excludeProductId,
  limit = 8,
}: {
  title: string;
  categoryId?: string;
  excludeProductId?: string;
  limit?: number;
}) {
  // Fetch one extra so that excluding the current product still leaves
  // us with `limit` items in the common case.
  const size = excludeProductId ? limit + 1 : limit;
  const sp = new URLSearchParams({ size: String(size) });
  if (categoryId) sp.set("categoryId", categoryId);

  let items: ProductResponse[] = [];
  try {
    const page = await serverGet<Page<ProductResponse>>(`/v1/products?${sp.toString()}`);
    items = page.content ?? [];
  } catch (e) {
    if (!(e instanceof ServerFetchError)) throw e;
    items = [];
  }

  if (excludeProductId) {
    items = items.filter((p) => p.id !== excludeProductId);
  }
  items = items.slice(0, limit);

  if (items.length === 0) return null;

  return (
    <section aria-label={title} className="mt-10">
      <h2 className="font-display text-2xl font-semibold">{title}</h2>
      <div className="mt-4 flex gap-4 overflow-x-auto pb-2 snap-x snap-mandatory">
        {items.map((p) => (
          <div key={p.id} className="min-w-[240px] max-w-[260px] snap-start">
            <ProductCard product={p} />
          </div>
        ))}
      </div>
    </section>
  );
}
