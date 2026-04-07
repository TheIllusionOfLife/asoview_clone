import { TicketsClient } from "./TicketsClient";

type Props = {
  params: Promise<{ orderId: string }>;
};

/**
 * Shell-SSR ticket page. Server renders the title; client fetches the
 * owner-checked ticket list via GET /v1/me/tickets?orderId={orderId}.
 *
 * Cross-user note: the backend filter returns 200 [] for orders the
 * caller does not own (the endpoint is a user-scoped filtered list, not
 * a sub-resource). The empty list is rendered as "not found" so the
 * UX still communicates that the page is unreachable for foreign orders.
 */
export default async function TicketsPage({ params }: Props) {
  const { orderId } = await params;
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">チケット</h1>
      <TicketsClient orderId={orderId} />
    </div>
  );
}
