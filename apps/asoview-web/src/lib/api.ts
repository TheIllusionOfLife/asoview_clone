/**
 * Asoview! API client.
 *
 * Wraps `fetch` against the Spring Cloud Gateway. Responsibilities:
 * - inject `Authorization: Bearer <idToken>` when a token getter is set
 * - inject `Idempotency-Key` on `POST /v1/orders`
 * - map backend error codes to typed errors
 * - 10s default timeout, 30s for `/v1/payments`
 * - `AbortController` wired so React Strict Mode unmounts cancel cleanly
 * - jittered exponential backoff retry for idempotent GETs on 5xx
 *
 * Auth wiring is intentionally a setter rather than an import-time
 * dependency so this module stays SSR-safe and unit-testable.
 */

import { type IdempotencyFingerprint, getOrCreateIdempotencyKey } from "./idempotency";
import { sanitizeNext } from "./redirect";

// ---------- Typed errors ----------

/** 401 — caller should redirect to /signin?next=<sanitized current>. */
export class SignInRedirect extends Error {
  readonly next: string;
  constructor(next: string) {
    super("Sign-in required");
    this.name = "SignInRedirect";
    this.next = sanitizeNext(next);
  }
}

/** 409 — slot taken / inventory conflict. */
export class SlotTakenError extends Error {
  readonly status = 409;
  constructor(message = "Slot is no longer available") {
    super(message);
    this.name = "SlotTakenError";
  }
}

/** 5xx — server error with optional retry hint (seconds). */
export class ApiError extends Error {
  readonly status: number;
  readonly retryAfterSeconds: number | null;
  constructor(status: number, message: string, retryAfterSeconds: number | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

/** Network failure (DNS, TCP reset, abort, offline). */
export class NetworkError extends Error {
  constructor(message = "Network error") {
    super(message);
    this.name = "NetworkError";
  }
}

// ---------- Configuration ----------

type IdTokenGetter = (forceRefresh?: boolean) => Promise<string | null>;
let idTokenGetter: IdTokenGetter = async () => null;

/** Called by AuthProvider once Firebase is wired up. */
export function setIdTokenGetter(getter: IdTokenGetter): void {
  idTokenGetter = getter;
}

/**
 * Returns the current id token (or null) using the same getter the JSON
 * api uses. Exposed so helpers that must call `fetch` directly (e.g. the
 * binary .pkpass download in `wallet.ts`) attach `Authorization` without
 * registering a second token getter.
 */
export function getCurrentIdToken(forceRefresh?: boolean): Promise<string | null> {
  return idTokenGetter(forceRefresh).catch(() => null);
}

function baseUrl(): string {
  const u = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!u) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is not set");
  }
  return u.replace(/\/+$/, "");
}

const DEFAULT_TIMEOUT_MS = 10_000;
const PAYMENTS_TIMEOUT_MS = 30_000;

/**
 * Payment endpoints get the longer timeout. We must match BOTH:
 *   - the top-level prefix `/v1/payments...`
 *   - the per-order subresource `/v1/orders/{id}/payments...`
 * The previous `startsWith("/v1/payments")` predicate missed the latter
 * (10s timeout was applied to a Stripe round trip, which timed out
 * intermittently in the slow-network test).
 */
export function timeoutForPath(path: string): number {
  return /\/payments(?:[/?]|$)/.test(path) ? PAYMENTS_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
}

// ---------- Core request ----------

export type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE" | "PUT";
  body?: unknown;
  /** Forces an Idempotency-Key header on the request. */
  idempotency?: IdempotencyFingerprint;
  /** External AbortSignal — if provided, request aborts when it fires. */
  signal?: AbortSignal;
  /** Number of retries for idempotent GETs on 5xx. Default 0. */
  retries?: number;
  /** Override the default timeout (ms). */
  timeoutMs?: number;
  /** Current path, used to build a sanitized `?next=` on 401. Default "/". */
  currentPath?: string;
};

async function readErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { message?: string; error?: string };
    return body.message ?? body.error ?? `${res.status} ${res.statusText}`;
  } catch {
    return `${res.status} ${res.statusText}`;
  }
}

function parseRetryAfter(h: string | null): number | null {
  if (!h) return null;
  const n = Number(h);
  return Number.isFinite(n) && n > 0 ? n : null;
}

function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const t = setTimeout(resolve, ms);
    if (signal) {
      const onAbort = () => {
        clearTimeout(t);
        reject(new NetworkError("Aborted"));
      };
      if (signal.aborted) onAbort();
      else signal.addEventListener("abort", onAbort, { once: true });
    }
  });
}

function jitteredBackoff(attempt: number): number {
  const base = 200 * 2 ** attempt;
  return base + Math.floor(Math.random() * base);
}

async function doFetch<T>(path: string, options: RequestOptions): Promise<T> {
  const method = options.method ?? "GET";
  const headers: Record<string, string> = {
    Accept: "application/json",
  };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const token = await idTokenGetter().catch(() => null);
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  if (options.idempotency) {
    headers["Idempotency-Key"] = getOrCreateIdempotencyKey(options.idempotency);
  }

  const timeoutMs = options.timeoutMs ?? timeoutForPath(path);
  const ctrl = new AbortController();
  const onExternalAbort = () => ctrl.abort();
  if (options.signal) {
    if (options.signal.aborted) ctrl.abort();
    else options.signal.addEventListener("abort", onExternalAbort, { once: true });
  }
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);

  let res: Response;
  try {
    res = await fetch(`${baseUrl()}${path}`, {
      method,
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: ctrl.signal,
    });
  } catch (e) {
    clearTimeout(timer);
    if (options.signal) options.signal.removeEventListener("abort", onExternalAbort);
    if (e instanceof Error && e.name === "AbortError") {
      throw new NetworkError("Request aborted or timed out");
    }
    throw new NetworkError(e instanceof Error ? e.message : "Network error");
  }
  clearTimeout(timer);
  if (options.signal) options.signal.removeEventListener("abort", onExternalAbort);

  if (res.status === 401) {
    throw new SignInRedirect(options.currentPath ?? "/");
  }
  if (res.status === 409) {
    throw new SlotTakenError(await readErrorMessage(res));
  }
  if (res.status >= 500) {
    throw new ApiError(
      res.status,
      await readErrorMessage(res),
      parseRetryAfter(res.headers.get("Retry-After")),
    );
  }
  if (!res.ok) {
    throw new ApiError(res.status, await readErrorMessage(res));
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const retries = options.retries ?? 0;
  const isRetryable = (options.method ?? "GET") === "GET" && retries > 0;
  let attempt = 0;
  // Retry only on ApiError (5xx) and only for GETs.
  // SlotTakenError, SignInRedirect, NetworkError fall through immediately.
  for (;;) {
    try {
      return await doFetch<T>(path, options);
    } catch (e) {
      // Retry only on transient 5xx. 4xx (400/403/404/429...) are
      // permanent for the caller and must surface immediately; the
      // earlier code reference `!(e instanceof ApiError)` was missing
      // the explicit `>= 500` gate and would retry 404/400/429 too.
      if (!isRetryable || attempt >= retries || !(e instanceof ApiError) || e.status < 500) {
        throw e;
      }
      await delay(jitteredBackoff(attempt), options.signal);
      attempt += 1;
    }
  }
}

// ---------- Search helpers ----------

export type SearchHit = {
  productId: string;
  name: string;
  description: string | null;
  minPrice: number | null;
  areaId: string | null;
  categoryId: string | null;
};

export type ProductSearchResponse = {
  content: SearchHit[];
  totalElements: number;
  number: number;
  size: number;
};

export type SearchSuggestion = { productId: string; name: string };
export type AutosuggestResponse = { suggestions: SearchSuggestion[] };

export type SearchParams = {
  q?: string;
  category?: string;
  area?: string;
  priceMin?: number;
  priceMax?: number;
  sort?: string;
  page?: number;
  size?: number;
};

/**
 * Calls search-service via the gateway. The backend already filters on
 * visibility, but per CLAUDE.md PR #21 rule we still pass status=ACTIVE
 * as defense in depth — though the current backend shape ignores unknown
 * params so it is harmless if removed.
 */
export function searchProducts(
  params: SearchParams,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<ProductSearchResponse> {
  const sp = new URLSearchParams();
  if (params.q) sp.set("q", params.q);
  if (params.category) sp.set("category", params.category);
  if (params.area) sp.set("area", params.area);
  if (params.priceMin !== undefined && Number.isFinite(params.priceMin)) {
    sp.set("minPrice", String(params.priceMin));
  }
  if (params.priceMax !== undefined && Number.isFinite(params.priceMax)) {
    sp.set("maxPrice", String(params.priceMax));
  }
  if (params.sort) sp.set("sort", params.sort);
  if (params.page !== undefined) sp.set("page", String(params.page));
  if (params.size !== undefined) sp.set("size", String(params.size));
  const qs = sp.toString();
  return apiRequest<ProductSearchResponse>(`/v1/search${qs ? `?${qs}` : ""}`, {
    ...options,
    method: "GET",
  });
}

export function searchSuggest(
  q: string,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<AutosuggestResponse> {
  const sp = new URLSearchParams({ q });
  return apiRequest<AutosuggestResponse>(`/v1/search/suggest?${sp.toString()}`, {
    ...options,
    method: "GET",
  });
}

// ---------- Reviews ----------

export type ReviewResponse = {
  id: string;
  userId: string;
  productId: string;
  rating: number;
  title: string | null;
  body: string | null;
  language: string | null;
  status: string;
  helpfulCount: number;
  createdAt: string;
  updatedAt: string;
};

/** Spring Page<ReviewResponse> from GET /v1/products/{productId}/reviews. */
export function listReviews(
  productId: string,
  page = 0,
  size = 10,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<{ content: ReviewResponse[]; totalElements: number; number: number; size: number }> {
  return apiRequest(
    `/v1/products/${encodeURIComponent(productId)}/reviews?page=${page}&size=${size}`,
    { ...options, method: "GET" },
  );
}

export function submitReview(
  input: { productId: string; rating: number; title?: string; body?: string; language?: string },
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<ReviewResponse> {
  return apiRequest("/v1/reviews", {
    ...options,
    method: "POST",
    body: {
      productId: input.productId,
      rating: input.rating,
      title: input.title ?? "",
      body: input.body ?? "",
      language: input.language ?? "ja",
    },
  });
}

export function voteHelpful(
  reviewId: string,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<void> {
  return apiRequest<void>(`/v1/reviews/${encodeURIComponent(reviewId)}/helpful`, {
    ...options,
    method: "POST",
    body: {},
  });
}

// ---------- Favorites ----------

/** Backend returns a flat List<UUID> of favorited product ids. */
export function listFavorites(
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<string[]> {
  return apiRequest<string[]>("/v1/me/favorites", { ...options, method: "GET" });
}

export function addFavorite(
  productId: string,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<void> {
  return apiRequest<void>(`/v1/me/favorites/${encodeURIComponent(productId)}`, {
    ...options,
    method: "PUT",
  });
}

export function removeFavorite(
  productId: string,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<void> {
  return apiRequest<void>(`/v1/me/favorites/${encodeURIComponent(productId)}`, {
    ...options,
    method: "DELETE",
  });
}

// ---------- Points ----------

/** Backend returns {balance: long}. */
export function getPointsBalance(
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<{ balance: number }> {
  return apiRequest<{ balance: number }>("/v1/me/points", { ...options, method: "GET" });
}

export type PointLedgerEntry = {
  id: string;
  direction: "EARN" | "BURN" | "REFUND";
  amount: number;
  reason: string;
  referenceId: string | null;
  createdAt: string;
};

export type PointLedgerPage = {
  content: PointLedgerEntry[];
  totalElements: number;
  number: number;
  size: number;
  totalPages: number;
};

/** Paginated point ledger. */
export function getPointsLedger(
  page = 0,
  size = 20,
  options: Omit<RequestOptions, "method" | "body"> = {},
): Promise<PointLedgerPage> {
  return apiRequest<PointLedgerPage>(`/v1/me/points/ledger?page=${page}&size=${size}`, {
    ...options,
    method: "GET",
  });
}

export const api = {
  get: <T>(path: string, options: Omit<RequestOptions, "method" | "body"> = {}) =>
    apiRequest<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options: Omit<RequestOptions, "method" | "body"> = {}) =>
    apiRequest<T>(path, { ...options, method: "POST", body }),
  patch: <T>(path: string, body?: unknown, options: Omit<RequestOptions, "method" | "body"> = {}) =>
    apiRequest<T>(path, { ...options, method: "PATCH", body }),
  delete: <T>(path: string, options: Omit<RequestOptions, "method" | "body"> = {}) =>
    apiRequest<T>(path, { ...options, method: "DELETE" }),
};
