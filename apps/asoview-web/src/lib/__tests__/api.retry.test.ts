/**
 * Pitfall 10 (PR #22): `apiRequest` retries idempotent GETs on 5xx ONLY. Any
 * regression that retries 4xx (404 / 409 / 429) wastes upstream capacity and
 * delays user feedback. The previous gate `!(e instanceof ApiError)` was
 * missing the explicit `>= 500` check.
 *
 * Matrix: 500 retries (3 calls total with retries=2), 502 retries, 404 does
 * NOT retry, 429 does NOT retry, 409 does NOT retry. SignInRedirect (401)
 * is intentionally not in the matrix because it short-circuits before the
 * retry loop.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const ORIG_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

describe("apiRequest retry gating (Pitfall 10)", () => {
  beforeEach(() => {
    process.env.NEXT_PUBLIC_API_BASE_URL = "http://localhost:8080";
    vi.resetModules();
  });

  afterEach(() => {
    process.env.NEXT_PUBLIC_API_BASE_URL = ORIG_BASE_URL;
    vi.restoreAllMocks();
  });

  function mockResponse(status: number): Response {
    return new Response(JSON.stringify({ message: `HTTP ${status}` }), {
      status,
      headers: { "Content-Type": "application/json" },
    });
  }

  it.each<[number, number]>([
    [500, 3],
    [502, 3],
    [404, 1],
    [429, 1],
    [409, 1],
  ])("status %i → %i fetch calls", async (status, expectedCalls) => {
    const fetchMock = vi.fn().mockResolvedValue(mockResponse(status));
    vi.stubGlobal("fetch", fetchMock);

    const { apiRequest } = await import("../api");

    await expect(apiRequest("/v1/something", { retries: 2, timeoutMs: 1_000 })).rejects.toThrow();

    expect(fetchMock).toHaveBeenCalledTimes(expectedCalls);
  });
});
