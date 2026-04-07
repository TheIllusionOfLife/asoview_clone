/**
 * Checkout page stub. The full polling + payment flow lands in Session D
 * (PR 3d Session D / PR 3e). Today this page just confirms the order id
 * landed and gives the user a path back so end-to-end navigation works.
 */

type Props = {
  params: Promise<{ orderId: string }>;
};

export default async function CheckoutPage({ params }: Props) {
  const { orderId } = await params;
  return (
    <div className="mx-auto max-w-2xl px-4 py-16 text-center">
      <h1 className="font-display text-3xl font-bold">注文を受け付けました</h1>
      <p className="mt-3 text-sm text-[var(--color-ink-muted)]">
        Checkout (Session D) — order id: <code className="font-mono">{orderId}</code>
      </p>
      <p className="mt-6 text-sm">決済フローはまもなくこの画面に実装されます。</p>
    </div>
  );
}
