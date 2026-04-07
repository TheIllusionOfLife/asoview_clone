import { AreaCard } from "@/components/AreaCard";
import { ProductCard } from "@/components/ProductCard";
import { serverGet } from "@/lib/server-api";
import type { AreaResponse, Page, ProductResponse } from "@/lib/types";

export const revalidate = 60;

async function loadAreas(): Promise<AreaResponse[]> {
  try {
    const page = await serverGet<Page<AreaResponse>>("/v1/areas?size=24");
    return page.content ?? [];
  } catch {
    return [];
  }
}

async function loadFeatured(): Promise<ProductResponse[]> {
  try {
    const page = await serverGet<Page<ProductResponse>>("/v1/products?size=8");
    return page.content ?? [];
  } catch {
    return [];
  }
}

export default async function HomePage() {
  const [areas, featured] = await Promise.all([loadAreas(), loadFeatured()]);

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
      <section className="text-center max-w-2xl mx-auto">
        <h1 className="font-display text-4xl sm:text-5xl font-bold leading-tight">
          体験を、もっと身近に。
        </h1>
        <p className="mt-4 text-[var(--color-ink-muted)]">
          全国のレジャー・アクティビティ・体験をかんたん予約。
        </p>
      </section>

      <section aria-labelledby="areas-heading" className="mt-14">
        <h2 id="areas-heading" className="font-display text-2xl font-semibold">
          エリアから探す
        </h2>
        {areas.length === 0 ? (
          <p className="mt-4 text-sm text-[var(--color-ink-muted)]">
            エリア情報を読み込めませんでした。
          </p>
        ) : (
          <div className="mt-5 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            {areas.map((a) => (
              <AreaCard key={a.id} area={a} />
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="featured-heading" className="mt-14">
        <h2 id="featured-heading" className="font-display text-2xl font-semibold">
          注目の体験
        </h2>
        {featured.length === 0 ? (
          <p className="mt-4 text-sm text-[var(--color-ink-muted)]">
            おすすめ商品を読み込めませんでした。
          </p>
        ) : (
          <div className="mt-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
            {featured.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
