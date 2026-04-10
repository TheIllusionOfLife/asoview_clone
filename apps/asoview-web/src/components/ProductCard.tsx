import { Link } from "@/i18n/navigation";
import type { ProductResponse } from "@/lib/types";
import { useLocale } from "next-intl";
import { FavoriteToggle } from "./favorites/FavoriteToggle";

function formatJpy(amount: number | undefined, locale: string): string {
  if (amount == null || !Number.isFinite(amount)) return "—";
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 0,
  }).format(Math.trunc(amount));
}

export function ProductCard({ product }: { product: ProductResponse }) {
  const locale = useLocale();
  const minPrice = product.variants?.[0]?.priceAmount;
  // The FavoriteToggle is a <button> and must NOT be nested inside the
  // <Link> (<button> in <a> is invalid HTML). Render the toggle as an
  // absolutely-positioned sibling of the Link inside a relative wrapper.
  return (
    <div className="group relative rounded-[var(--radius-lg)] bg-[var(--color-surface)] border border-[var(--color-border)] overflow-hidden shadow-[var(--shadow-sm)] hover:shadow-[var(--shadow-md)] motion-safe:transition-shadow">
      <Link href={`/products/${product.id}`} className="block">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.title} className="aspect-[4/3] w-full object-cover" />
        ) : (
          <div className="aspect-[4/3] bg-gradient-to-br from-[var(--color-primary)]/15 to-[var(--color-accent)]/10" />
        )}
        <div className="p-4">
          <h3 className="font-display text-lg font-semibold line-clamp-2 group-hover:text-[var(--color-primary)]">
            {product.title}
          </h3>
          {product.description && (
            <p className="mt-1 text-sm text-[var(--color-ink-muted)] line-clamp-2">
              {product.description}
            </p>
          )}
          <p className="mt-3 text-base font-semibold text-[var(--color-primary)]">
            {formatJpy(minPrice, locale)}
            <span className="text-xs text-[var(--color-ink-muted)] font-normal"> 〜</span>
          </p>
        </div>
      </Link>
      <div className="absolute top-2 right-2 z-10">
        <FavoriteToggle productId={product.id} />
      </div>
    </div>
  );
}
