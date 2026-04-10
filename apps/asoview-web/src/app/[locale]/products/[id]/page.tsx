import { SlotPicker } from "@/components/SlotPicker";
import { FavoriteToggle } from "@/components/favorites/FavoriteToggle";
import { SimilarProducts } from "@/components/recommendations/SimilarProducts";
import { ReviewForm } from "@/components/reviews/ReviewForm";
import { ReviewList } from "@/components/reviews/ReviewList";
import { Link } from "@/i18n/navigation";
import { ServerFetchError, serverGet } from "@/lib/server-api";
import type { ProductResponse } from "@/lib/types";
import { getTranslations } from "next-intl/server";
import { notFound } from "next/navigation";

export const revalidate = 60;

type Props = {
  params: Promise<{ locale: string; id: string }>;
};

function formatJpy(amount: number | undefined, locale: string): string {
  if (amount == null || !Number.isFinite(amount)) return "—";
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 0,
  }).format(Math.trunc(amount));
}

/**
 * Fetches a product scoped to the caller's active locale. The gateway
 * resolves the request's `lang` against the V14 `translations` JSONB
 * column and returns `name` / `description` already localized. Unknown
 * locales fall back to the default `ja` content server-side, so we
 * don't need a client fallback.
 */
async function loadProduct(id: string, lang: string): Promise<ProductResponse | null> {
  try {
    return await serverGet<ProductResponse>(
      `/v1/products/${encodeURIComponent(id)}?lang=${encodeURIComponent(lang)}`,
    );
  } catch (e) {
    if (e instanceof ServerFetchError && e.status === 404) return null;
    throw e;
  }
}

export default async function ProductPage({ params }: Props) {
  const { locale, id } = await params;
  const [product, tRec] = await Promise.all([
    loadProduct(id, locale),
    getTranslations("recommendations"),
  ]);
  // Visibility gate: never render non-ACTIVE products on the public detail page.
  if (!product || product.status !== "ACTIVE") {
    notFound();
  }
  const minPrice = product.variants?.[0]?.priceAmount;

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <nav className="text-sm text-[var(--color-ink-muted)]">
        <Link href="/" className="hover:text-[var(--color-primary)]">
          ホーム
        </Link>
        <span className="mx-2">/</span>
        <span>{product.title}</span>
      </nav>

      <div className="mt-4 grid grid-cols-1 lg:grid-cols-[1.4fr_1fr] gap-8">
        <div>
          {product.imageUrl ? (
            <img src={product.imageUrl} alt={product.title} className="aspect-[4/3] w-full rounded-[var(--radius-lg)] object-cover" />
          ) : (
            <div className="aspect-[4/3] rounded-[var(--radius-lg)] bg-gradient-to-br from-[var(--color-primary)]/20 to-[var(--color-accent)]/10" />
          )}
          <div className="mt-5 flex items-start justify-between gap-3">
            <h1 className="font-display text-3xl font-bold">{product.title}</h1>
            <FavoriteToggle productId={product.id} />
          </div>
          <p className="mt-2 text-base font-semibold text-[var(--color-primary)]">
            {formatJpy(minPrice, locale)}
            <span className="text-sm text-[var(--color-ink-muted)] font-normal"> 〜 / 名</span>
          </p>
          {product.description && (
            <p className="mt-4 whitespace-pre-line leading-7 text-[var(--color-ink)]">
              {product.description}
            </p>
          )}
        </div>
        <SlotPicker product={product} />
      </div>

      <section className="mt-12">
        <h2 className="font-display text-2xl font-bold mb-4">レビュー</h2>
        <ReviewList productId={product.id} />
        <div className="mt-6">
          <ReviewForm productId={product.id} />
        </div>
      </section>

      <SimilarProducts
        title={tRec("similar")}
        categoryId={product.categoryId ?? undefined}
        excludeProductId={product.id}
      />
    </div>
  );
}
