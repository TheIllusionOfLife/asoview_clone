"use client";

/**
 * /checkout/[orderId] client.
 *
 * Lifecycle:
 *  1. GET /v1/orders/{id} — owner-checked. 404 → "order not found" UX
 *     (cross-user surfaces here because the backend masks ownership as 404).
 *  2. POST /v1/orders/{id}/payments — idempotent. Returns the same
 *     `clientSecret` on replay so refresh-during-checkout reuses the
 *     existing payment intent rather than minting a second one.
 *  3. Branch:
 *       - provider=stripe: lazy-import @stripe/stripe-js + react-stripe-js,
 *         render Elements card form, call stripe.confirmPayment, then poll.
 *       - provider=paypay: render a redirect button (no SDK).
 *       - fakeMode=1 (env-gated by NEXT_PUBLIC_FAKE_CHECKOUT_MODE=1, throws
 *         in production builds): skip Stripe entirely and immediately start
 *         polling. The CI compose drives the order to PAID via a fake gateway
 *         on the backend; the harness only short-circuits the SDK.
 *  4. Poll GET /v1/orders/{id} every 1.5s. Terminal handling:
 *       - PAID → clear cart lines for this order's slotIds, clear the
 *         draft idempotency key, route to /tickets/{orderId}.
 *       - CANCELLED / FAILED → failure UX, stop polling.
 *       - 30s still in PAYMENT_PENDING → "taking longer" UX with a link
 *         to /me/orders, stop polling.
 *       - CONFIRMING → keep polling (transient).
 */

import { ApiError, NetworkError, SignInRedirect, api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { clearCart, readCart, writeCart } from "@/lib/cart";
import type { OrderResponse, OrderStatus } from "@/lib/types";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

type Provider = "stripe" | "paypay";

type PaymentIntentResponse = {
  paymentId: string;
  status: string;
  providerPaymentId: string | null;
  clientSecret: string | null;
};

const POLL_INTERVAL_MS = 1500;
const POLL_TIMEOUT_MS = 30_000;
const TERMINAL_STATES: ReadonlySet<OrderStatus> = new Set(["PAID", "CANCELLED", "FAILED"]);

function assertFakeModeAllowed(): void {
  const enabled = process.env.NEXT_PUBLIC_FAKE_CHECKOUT_MODE === "1";
  if (!enabled) {
    // Defensive: throw aggressively in any non-fake build so a stray
    // ?fakeMode=1 in production cannot bypass payment.
    throw new Error(
      "fakeMode harness is disabled. Set NEXT_PUBLIC_FAKE_CHECKOUT_MODE=1 to enable in dev/test only.",
    );
  }
  if (process.env.NODE_ENV === "production") {
    throw new Error("fakeMode harness must never run in production builds");
  }
}

function clearOrderLinesFromCart(uid: string | null, order: OrderResponse): void {
  const slotIds = new Set(order.items.map((i) => i.slotId));
  const cart = readCart(uid);
  const remaining = cart.lines.filter((l) => !slotIds.has(l.slotId));
  if (remaining.length === 0) {
    clearCart(uid);
  } else {
    writeCart(uid, { lines: remaining });
  }
}

export function CheckoutClient({
  orderId,
  provider,
  fakeMode,
}: {
  orderId: string;
  provider: Provider;
  fakeMode: boolean;
}) {
  const router = useRouter();
  const { user, ready } = useAuth();
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [intent, setIntent] = useState<PaymentIntentResponse | null>(null);
  const [phase, setPhase] = useState<
    | "loading"
    | "ready"
    | "confirming"
    | "polling"
    | "succeeded"
    | "failed"
    | "delayed"
    | "not-found"
    | "error"
  >("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const pollStartedAt = useRef<number | null>(null);

  // Polling loop trigger; used by Stripe success, PayPay redirect, and fakeMode.
  const startPolling = useCallback(() => {
    if (pollStartedAt.current !== null) return;
    pollStartedAt.current = Date.now();
    setPhase("polling");
  }, []);

  // 1. Fetch order + 2. create payment intent.
  useEffect(() => {
    if (!ready) return;
    let cancelled = false;
    const ctrl = new AbortController();

    (async () => {
      try {
        if (fakeMode) assertFakeModeAllowed();
        const fetchedOrder = await api.get<OrderResponse>(`/v1/orders/${orderId}`, {
          signal: ctrl.signal,
          currentPath: `/checkout/${orderId}`,
        });
        if (cancelled) return;
        setOrder(fetchedOrder);

        // If the order is already terminal, skip the intent and surface state.
        if (fetchedOrder.status === "PAID") {
          setPhase("succeeded");
          if (user) clearOrderLinesFromCart(user.uid, fetchedOrder);
          router.replace(`/tickets/${orderId}`);
          return;
        }
        if (fetchedOrder.status === "CANCELLED" || fetchedOrder.status === "FAILED") {
          setPhase("failed");
          return;
        }

        // Idempotent intent: replays return the same clientSecret because
        // the backend keys on (orderId) and persists clientSecret on the row.
        const created = await api.post<PaymentIntentResponse>(
          `/v1/orders/${orderId}/payments`,
          { provider: provider.toUpperCase() },
          { signal: ctrl.signal, currentPath: `/checkout/${orderId}` },
        );
        if (cancelled) return;
        setIntent(created);
        setPhase("ready");

        if (fakeMode) {
          // Fake harness: skip the SDK; the backend's fake gateway will
          // flip the order asynchronously. Just start polling.
          startPolling();
        }
      } catch (e) {
        if (cancelled) return;
        if (e instanceof SignInRedirect) {
          router.push(`/signin?next=${encodeURIComponent(e.next)}`);
          return;
        }
        if (e instanceof ApiError && e.status === 404) {
          setPhase("not-found");
          return;
        }
        setErrorMessage(
          e instanceof ApiError || e instanceof NetworkError
            ? e.message
            : "決済の準備中にエラーが発生しました",
        );
        setPhase("error");
      }
    })();

    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [ready, orderId, provider, fakeMode, router, user, startPolling]);

  useEffect(() => {
    if (phase !== "polling") return;
    let stopped = false;
    const ctrl = new AbortController();

    const tick = async () => {
      if (stopped) return;
      try {
        const fetched = await api.get<OrderResponse>(`/v1/orders/${orderId}`, {
          signal: ctrl.signal,
          currentPath: `/checkout/${orderId}`,
        });
        if (stopped) return;
        setOrder(fetched);
        if (fetched.status === "PAID") {
          stopped = true;
          if (user) clearOrderLinesFromCart(user.uid, fetched);
          setPhase("succeeded");
          router.replace(`/tickets/${orderId}`);
          return;
        }
        if (fetched.status === "CANCELLED" || fetched.status === "FAILED") {
          stopped = true;
          setPhase("failed");
          return;
        }
        // CONFIRMING / PAYMENT_PENDING / PENDING → keep polling.
        const elapsed = Date.now() - (pollStartedAt.current ?? Date.now());
        if (elapsed >= POLL_TIMEOUT_MS) {
          stopped = true;
          setPhase("delayed");
          return;
        }
      } catch (e) {
        if (stopped) return;
        if (e instanceof NetworkError) {
          // Transient; the loop will retry on the next tick.
        } else if (e instanceof ApiError && e.status === 404) {
          stopped = true;
          setPhase("not-found");
          return;
        }
      }
    };

    const id = setInterval(tick, POLL_INTERVAL_MS);
    void tick();
    return () => {
      stopped = true;
      clearInterval(id);
      ctrl.abort();
    };
  }, [phase, orderId, router, user]);

  // ---------- UI ----------

  if (phase === "loading") {
    return <p className="mt-6 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>;
  }
  if (phase === "not-found") {
    return (
      <div className="mt-6">
        <p className="text-sm">この注文は見つかりませんでした。</p>
        <Link href="/me/orders" className="mt-3 inline-block text-sm text-[var(--color-primary)]">
          予約履歴へ
        </Link>
      </div>
    );
  }
  if (phase === "error") {
    return (
      <div role="alert" className="mt-6 text-sm text-[var(--color-danger)]">
        {errorMessage ?? "エラー"}
      </div>
    );
  }
  if (phase === "failed") {
    return (
      <div role="alert" className="mt-6 space-y-3">
        <p className="text-sm text-[var(--color-danger)]">決済に失敗しました。</p>
        <div className="flex gap-3">
          <Link href="/me/orders" className="text-sm text-[var(--color-primary)]">
            予約履歴を見る
          </Link>
          <Link href="/" className="text-sm text-[var(--color-primary)]">
            もう一度試す
          </Link>
        </div>
      </div>
    );
  }
  if (phase === "delayed") {
    return (
      <div className="mt-6 space-y-3">
        <p className="text-sm">
          決済処理に時間がかかっています。完了次第、予約履歴に反映されます。
        </p>
        <Link href="/me/orders" className="text-sm text-[var(--color-primary)]">
          予約履歴へ
        </Link>
      </div>
    );
  }
  if (phase === "succeeded") {
    return <p className="mt-6 text-sm">決済が完了しました。チケットページへ移動します…</p>;
  }
  if (phase === "polling" || phase === "confirming") {
    return (
      <output className="mt-6 block text-sm text-[var(--color-ink-muted)]" aria-live="polite">
        決済を確認しています…
      </output>
    );
  }

  // phase === "ready"
  return (
    <div className="mt-6 space-y-4">
      <p className="text-sm text-[var(--color-ink-muted)]">
        合計:{" "}
        <strong>
          {order?.totalAmount} {order?.currency}
        </strong>
      </p>
      {fakeMode ? (
        <p className="text-xs text-[var(--color-ink-muted)]">[fakeMode] 決済を待機中…</p>
      ) : provider === "paypay" ? (
        <PayPayRedirectButton intent={intent} onRedirect={startPolling} />
      ) : (
        <StripeFormLoader
          clientSecret={intent?.clientSecret ?? null}
          onConfirmed={startPolling}
          onError={(msg) => {
            setErrorMessage(msg);
            setPhase("error");
          }}
        />
      )}
    </div>
  );
}

// ---------- Stripe form (lazy-loaded) ----------

function StripeFormLoader({
  clientSecret,
  onConfirmed,
  onError,
}: {
  clientSecret: string | null;
  onConfirmed: () => void;
  onError: (msg: string) => void;
}) {
  const [LazyForm, setLazyForm] = useState<React.ComponentType<{
    clientSecret: string;
    onConfirmed: () => void;
    onError: (msg: string) => void;
  }> | null>(null);

  useEffect(() => {
    let cancelled = false;
    if (!clientSecret) return;
    (async () => {
      try {
        // Lazy-import keeps Stripe out of the landing JS budget.
        const [{ loadStripe }, reactStripe] = await Promise.all([
          import("@stripe/stripe-js"),
          import("@stripe/react-stripe-js"),
        ]);
        if (cancelled) return;
        const pk = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY;
        if (!pk) {
          onError("Stripe publishable key is not configured");
          return;
        }
        const stripePromise = loadStripe(pk);
        const { Elements, PaymentElement, useStripe, useElements } = reactStripe;

        function InnerForm({ onConfirmed: cb }: { onConfirmed: () => void }) {
          const stripe = useStripe();
          const elements = useElements();
          const [submitting, setSubmitting] = useState(false);
          const [err, setErr] = useState<string | null>(null);
          return (
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                if (!stripe || !elements) return;
                setSubmitting(true);
                setErr(null);
                const result = await stripe.confirmPayment({
                  elements,
                  redirect: "if_required",
                });
                if (result.error) {
                  setSubmitting(false);
                  setErr(result.error.message ?? "決済に失敗しました");
                  return;
                }
                cb();
              }}
              className="space-y-3"
            >
              <PaymentElement />
              {err && (
                <p role="alert" className="text-sm text-[var(--color-danger)]">
                  {err}
                </p>
              )}
              <button
                type="submit"
                disabled={!stripe || submitting}
                className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
              >
                {submitting ? "処理中…" : "支払う"}
              </button>
            </form>
          );
        }

        const Wrapper = (props: {
          clientSecret: string;
          onConfirmed: () => void;
          onError: (msg: string) => void;
        }) => (
          <Elements stripe={stripePromise} options={{ clientSecret: props.clientSecret }}>
            <InnerForm onConfirmed={props.onConfirmed} />
          </Elements>
        );
        setLazyForm(() => Wrapper);
      } catch (e) {
        onError(e instanceof Error ? e.message : "Stripe SDK failed to load");
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [clientSecret, onError]);

  if (!clientSecret) {
    return <p className="text-sm text-[var(--color-danger)]">決済情報を取得できませんでした。</p>;
  }
  if (!LazyForm) {
    return <p className="text-sm text-[var(--color-ink-muted)]">決済フォームを読み込み中…</p>;
  }
  return <LazyForm clientSecret={clientSecret} onConfirmed={onConfirmed} onError={onError} />;
}

// ---------- PayPay redirect ----------

function PayPayRedirectButton({
  intent,
  onRedirect,
}: {
  intent: PaymentIntentResponse | null;
  onRedirect: () => void;
}) {
  // The backend's PayPay gateway returns the redirect URL in clientSecret
  // (it doubles as the "next action" URL for redirect-based providers).
  // If a future contract change splits this into a separate field, update
  // here. The fallback "#" prevents an undefined-href crash; the button
  // is disabled when there's no URL.
  const url = intent?.clientSecret ?? null;
  return (
    <a
      href={url ?? "#"}
      onClick={() => {
        if (url) onRedirect();
      }}
      aria-disabled={!url}
      className="inline-block rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
    >
      PayPayで支払う
    </a>
  );
}
