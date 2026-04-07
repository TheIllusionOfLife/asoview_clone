import { Suspense } from "react";
import { MyOrdersClient } from "./MyOrdersClient";

// useSearchParams() requires the page to opt out of static prerendering.
export const dynamic = "force-dynamic";

/**
 * Shell-SSR /me/orders page. Backend currently returns a flat List
 * (not a Spring Page<T>) from /v1/me/orders, so pagination is performed
 * client-side over a single fetch. When the backend grows server-side
 * pagination this can swap to ?page= without changing the page contract.
 */
export default function MyOrdersPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">予約履歴</h1>
      <Suspense
        fallback={<p className="mt-6 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>}
      >
        <MyOrdersClient />
      </Suspense>
    </div>
  );
}
