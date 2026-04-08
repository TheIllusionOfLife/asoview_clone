// @vitest-environment jsdom
/**
 * Regression test for PR #23 Pitfall 14 (token-refresh re-init).
 *
 * Firebase's `onIdTokenChanged` fires every ~55 minutes and swaps the
 * `User` reference even though `uid` is unchanged. If CheckoutClient's
 * init effect depends on `user` (reference) instead of `user.uid`, a
 * silent token refresh mid-checkout would re-run the effect, re-call
 * `POST /v1/orders/{id}/payments` (benign on the backend because the
 * intent is idempotent on orderId) AND reset `phase` from "polling"
 * back to "ready", silently killing the polling loop.
 *
 * This test pins the invariant: swapping `user` to a new object with
 * the same `uid` must NOT re-run the init effect.
 */

import { act, render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

// ---- Mocks (must be set up before importing the component under test) ----

const { getMock, postMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
}));

vi.mock("@/lib/api", () => {
  class SignInRedirect extends Error {
    next: string;
    constructor(next: string) {
      super("redirect");
      this.next = next;
    }
  }
  class ApiError extends Error {
    status: number;
    constructor(status: number, msg: string) {
      super(msg);
      this.status = status;
    }
  }
  class NetworkError extends Error {}
  return {
    api: { get: getMock, post: postMock },
    SignInRedirect,
    ApiError,
    NetworkError,
  };
});

let currentUser: { uid: string; getIdToken: () => Promise<string> } | null = null;
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: currentUser, ready: true }),
}));

const { stableRouter } = vi.hoisted(() => ({
  stableRouter: { push: () => {}, replace: () => {} },
}));
vi.mock("@/i18n/navigation", () => ({
  Link: ({ children }: { children: React.ReactNode }) => children,
  useRouter: () => stableRouter,
}));

// Avoid loading the real Stripe SDK during the test.
vi.mock("@stripe/stripe-js", () => ({ loadStripe: vi.fn().mockResolvedValue(null) }));
vi.mock("@stripe/react-stripe-js", () => ({
  Elements: ({ children }: { children: React.ReactNode }) => children,
  PaymentElement: () => null,
  useStripe: () => null,
  useElements: () => null,
}));

// Now import the component.
import { CheckoutClient } from "../CheckoutClient";

afterEach(() => {
  getMock.mockReset();
  postMock.mockReset();
  currentUser = null;
});

describe("CheckoutClient token refresh", () => {
  it("does not re-run init / re-POST payments when user reference swaps but uid is unchanged", async () => {
    currentUser = { uid: "u1", getIdToken: async () => "token-a" };

    getMock.mockResolvedValue({
      id: "test-order",
      status: "PENDING",
      totalAmount: 1500,
      currency: "JPY",
      items: [{ slotId: "s1" }],
    });
    postMock.mockResolvedValue({
      provider: "stripe",
      clientSecret: "pi_secret_abc",
      redirectUrl: null,
    });

    const { rerender } = render(<CheckoutClient orderId="test-order" fakeMode={false} />);

    await waitFor(() => {
      expect(postMock).toHaveBeenCalledTimes(1);
    });
    expect(postMock).toHaveBeenCalledWith("/v1/orders/test-order/payments", {}, expect.any(Object));

    // Simulate Firebase silent token refresh: new User object, same uid.
    currentUser = { uid: "u1", getIdToken: async () => "token-b" };
    rerender(<CheckoutClient orderId="test-order" fakeMode={false} />);

    // Flush microtasks so any (incorrectly-scheduled) effect would run.
    await new Promise((r) => setTimeout(r, 50));

    // The init effect must NOT have re-fired. Exactly one payments POST.
    expect(postMock).toHaveBeenCalledTimes(1);
  });
});
