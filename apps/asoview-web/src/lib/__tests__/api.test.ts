import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  NetworkError,
  SignInRedirect,
  SlotTakenError,
  apiRequest,
  setIdTokenGetter,
  timeoutForPath,
} from "../api";

const BASE = "http://test.local";

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

describe("apiRequest", () => {
  beforeEach(() => {
    process.env.NEXT_PUBLIC_API_BASE_URL = BASE;
    setIdTokenGetter(async () => "test-token");
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setIdTokenGetter(async () => null);
  });

  it("issues a GET and parses JSON", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse(200, { hello: "world" }));
    const data = await apiRequest<{ hello: string }>("/v1/foo");
    expect(data).toEqual({ hello: "world" });
    const init = fetchSpy.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer test-token");
  });

  it("includes Idempotency-Key when fingerprint provided", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(jsonResponse(200, {}));
    await apiRequest("/v1/orders", {
      method: "POST",
      body: { foo: 1 },
      idempotency: { productId: "p", slotId: "s", quantity: 1 },
    });
    const headers = (fetchSpy.mock.calls[0][1] as RequestInit).headers as Record<string, string>;
    expect(headers["Idempotency-Key"]).toMatch(/^[0-9a-f-]{36}$/i);
  });

  it("throws SignInRedirect on 401 with sanitized next", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      jsonResponse(401, { error: "unauthorized" }),
    );
    await expect(apiRequest("/v1/me", { currentPath: "/checkout/123" })).rejects.toMatchObject({
      name: "SignInRedirect",
      next: "/checkout/123",
    });
  });

  it("sanitizes a hostile currentPath on SignInRedirect", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(jsonResponse(401, {}));
    try {
      await apiRequest("/v1/me", { currentPath: "//evil.com/" });
      throw new Error("expected throw");
    } catch (e) {
      expect(e).toBeInstanceOf(SignInRedirect);
      expect((e as SignInRedirect).next).toBe("/");
    }
  });

  it("throws SlotTakenError on 409", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(jsonResponse(409, { message: "taken" }));
    await expect(apiRequest("/v1/orders", { method: "POST", body: {} })).rejects.toBeInstanceOf(
      SlotTakenError,
    );
  });

  it("throws ApiError on 500 with retryAfter", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(
      jsonResponse(503, { message: "down" }, { "Retry-After": "5" }),
    );
    try {
      await apiRequest("/v1/foo");
      throw new Error("expected throw");
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).status).toBe(503);
      expect((e as ApiError).retryAfterSeconds).toBe(5);
    }
  });

  it("retries idempotent GET on 5xx then succeeds", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse(500, { message: "boom" }))
      .mockResolvedValueOnce(jsonResponse(200, { ok: true }));
    const data = await apiRequest<{ ok: boolean }>("/v1/foo", { retries: 1 });
    expect(data).toEqual({ ok: true });
    expect(fetchSpy).toHaveBeenCalledTimes(2);
  });

  it("does not retry POSTs", async () => {
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(jsonResponse(500, { message: "boom" }));
    await expect(
      apiRequest("/v1/orders", { method: "POST", body: {}, retries: 3 }),
    ).rejects.toBeInstanceOf(ApiError);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });

  it("wraps fetch failures in NetworkError", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await expect(apiRequest("/v1/foo")).rejects.toBeInstanceOf(NetworkError);
  });

  it("timeoutForPath matches both /v1/payments and /v1/orders/{id}/payments", () => {
    expect(timeoutForPath("/v1/payments/intents")).toBe(30_000);
    expect(timeoutForPath("/v1/orders/ord-1/payments")).toBe(30_000);
    expect(timeoutForPath("/v1/orders/ord-1/payments?foo=bar")).toBe(30_000);
    expect(timeoutForPath("/v1/orders/ord-1/payments/123")).toBe(30_000);
    expect(timeoutForPath("/v1/orders/ord-1")).toBe(10_000);
    expect(timeoutForPath("/v1/products")).toBe(10_000);
    // Don't false-match a substring like "/v1/paymentsfoo".
    expect(timeoutForPath("/v1/paymentsfoo")).toBe(10_000);
  });

  it("aborts on external signal", async () => {
    const ctrl = new AbortController();
    vi.spyOn(globalThis, "fetch").mockImplementationOnce(
      (_input, init) =>
        new Promise((_resolve, reject) => {
          const sig = (init as RequestInit).signal as AbortSignal;
          const fail = () => {
            const err = new Error("aborted");
            err.name = "AbortError";
            reject(err);
          };
          if (sig.aborted) fail();
          else sig.addEventListener("abort", fail, { once: true });
        }),
    );
    ctrl.abort();
    const p = apiRequest("/v1/foo", { signal: ctrl.signal });
    await expect(p).rejects.toBeInstanceOf(NetworkError);
  });
});
