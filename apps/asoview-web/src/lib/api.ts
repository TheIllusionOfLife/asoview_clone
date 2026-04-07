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

function baseUrl(): string {
  const u = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!u) {
    throw new Error("NEXT_PUBLIC_API_BASE_URL is not set");
  }
  return u.replace(/\/+$/, "");
}

const DEFAULT_TIMEOUT_MS = 10_000;
const PAYMENTS_TIMEOUT_MS = 30_000;

function timeoutForPath(path: string): number {
  return path.startsWith("/v1/payments") ? PAYMENTS_TIMEOUT_MS : DEFAULT_TIMEOUT_MS;
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
      if (!isRetryable || attempt >= retries || !(e instanceof ApiError)) {
        throw e;
      }
      await delay(jitteredBackoff(attempt), options.signal);
      attempt += 1;
    }
  }
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
