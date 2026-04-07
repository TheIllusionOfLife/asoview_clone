import { SlotPicker } from "@/components/SlotPicker";
import { ServerFetchError, serverGet } from "@/lib/server-api";
import type { ProductResponse } from "@/lib/types";
import Link from "next/link";
import { notFound } from "next/navigation";

export const revalidate = 60;

type Props = {
  params: Promise<{ id: string }>;
};

function formatJpy(amount: string | undefined): string {
  if (!amount) return "—";
  const n = Number(amount);
  if (!Number.isFinite(n)) return amount;
  return new Intl.NumberFormat("ja-JP", {
    style: "currency",
    currency: "JPY",
    maximumFractionDigits: 0,
  }).format(Math.trunc(n));
}

async function loadProduct(id: string): Promise<ProductResponse | null> {
  try {
    return await serverGet<ProductResponse>(`/v1/products/${encodeURIComponent(id)}`);
  } catch (e) {
    if (e instanceof ServerFetchError && e.status === 404) return null;
    throw e;
  }
}

export default async function ProductPage({ params }: Props) {
  const { id } = await params;
  const product = await loadProduct(id);
  // Visibility gate: never render non-ACTIVE products on the public detail page.
  if (!product || product.status !== "ACTIVE") {
    notFound();
  }
  const minPrice = product.variants?.[0]?.unitPrice;

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <nav className="text-sm text-[var(--color-ink-muted)]">
        <Link href="/" className="hover:text-[var(--color-primary)]">
          ホーム
        </Link>
        <span className="mx-2">/</span>
        <span>{product.name}</span>
      </nav>

      <div className="mt-4 grid grid-cols-1 lg:grid-cols-[1.4fr_1fr] gap-8">
        <div>
          <div className="aspect-[4/3] rounded-[var(--radius-lg)] bg-gradient-to-br from-[var(--color-primary)]/20 to-[var(--color-accent)]/10" />
          <h1 className="mt-5 font-display text-3xl font-bold">{product.name}</h1>
          <p className="mt-2 text-base font-semibold text-[var(--color-primary)]">
            {formatJpy(minPrice)}
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
    </div>
  );
}
