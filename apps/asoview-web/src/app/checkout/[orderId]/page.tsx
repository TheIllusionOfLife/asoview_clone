import { CheckoutClient } from "./CheckoutClient";

type Props = {
  params: Promise<{ orderId: string }>;
  searchParams: Promise<{ provider?: string; fakeMode?: string }>;
};

/**
 * Shell-SSR checkout page. The server renders the skeleton; the client
 * fetches the authed order, creates a payment intent, and runs either
 * Stripe Elements, the PayPay redirect, or the env-gated fake-mode
 * harness. Polling + cart cleanup live entirely in the client component.
 */
export default async function CheckoutPage({ params, searchParams }: Props) {
  const { orderId } = await params;
  const sp = await searchParams;
  const provider = sp.provider === "paypay" ? "paypay" : "stripe";
  const fakeMode = sp.fakeMode === "1";
  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">お支払い</h1>
      <CheckoutClient orderId={orderId} provider={provider} fakeMode={fakeMode} />
    </div>
  );
}
