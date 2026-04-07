import { ProductCard } from "@/components/ProductCard";
import { serverGet } from "@/lib/server-api";
import type { AreaResponse, Page, ProductResponse } from "@/lib/types";
import Link from "next/link";
import { notFound } from "next/navigation";

export const revalidate = 60;

const PAGE_SIZE = 12;

type Props = {
  params: Promise<{ area: string }>;
  searchParams: Promise<{ page?: string }>;
};

/**
 * Resolve an area slug. Only swallows the "successful list, slug not
 * present" case as a 404. Upstream errors (timeout, 5xx, network) are
 * rethrown so Next renders error.tsx instead of pretending the area
 * doesn't exist.
 */
async function resolveArea(slug: string): Promise<AreaResponse | null> {
  const all = await serverGet<AreaResponse[]>("/v1/areas");
  return all.find((a) => a.slug === slug) ?? null;
}

async function loadProducts(areaId: string, pageNum: number): Promise<Page<ProductResponse>> {
  return await serverGet<Page<ProductResponse>>(
    `/v1/products?area=${encodeURIComponent(areaId)}&page=${pageNum}&size=${PAGE_SIZE}`,
  );
}

export default async function AreaPage({ params, searchParams }: Props) {
  const { area: slug } = await params;
  const sp = await searchParams;
  const pageNum = Math.max(0, Number.parseInt(sp.page ?? "0", 10) || 0);

  const area = await resolveArea(slug);
  if (!area) {
    notFound();
  }

  const result = await loadProducts(area.id, pageNum);
  const products = result.content ?? [];
  const totalPages = Math.max(1, Math.ceil(result.totalElements / PAGE_SIZE));
  const hasPrev = pageNum > 0;
  const hasNext = pageNum + 1 < totalPages;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <nav className="text-sm text-[var(--color-ink-muted)]">
        <Link href="/" className="hover:text-[var(--color-primary)]">
          ホーム
        </Link>
        <span className="mx-2">/</span>
        <span>{area.name}</span>
      </nav>

      <h1 className="font-display text-3xl font-bold mt-3">{area.name}の体験</h1>
      <p className="mt-1 text-sm text-[var(--color-ink-muted)]">
        {result.totalElements}件の体験が見つかりました
      </p>

      {products.length === 0 ? (
        <p className="mt-10 text-center text-[var(--color-ink-muted)]">
          このエリアには現在表示できる体験がありません。
        </p>
      ) : (
        <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
          {products.map((p) => (
            <ProductCard key={p.id} product={p} />
          ))}
        </div>
      )}

      {(hasPrev || hasNext) && (
        <nav aria-label="ページネーション" className="mt-10 flex items-center justify-center gap-3">
          {hasPrev ? (
            <Link
              href={`/areas/${slug}?page=${pageNum - 1}`}
              className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-2 text-sm hover:border-[var(--color-primary)]"
            >
              ← 前へ
            </Link>
          ) : (
            <span className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-4 py-2 text-sm text-[var(--color-ink-muted)] opacity-50">
              ← 前へ
            </span>
          )}
          <span className="text-sm text-[var(--color-ink-muted)]">
            {pageNum + 1} / {totalPages}
          </span>
          {hasNext ? (
            <Link
              href={`/areas/${slug}?page=${pageNum + 1}`}
              className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-2 text-sm hover:border-[var(--color-primary)]"
            >
              次へ →
            </Link>
          ) : (
            <span className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-4 py-2 text-sm text-[var(--color-ink-muted)] opacity-50">
              次へ →
            </span>
          )}
        </nav>
      )}
    </div>
  );
}
