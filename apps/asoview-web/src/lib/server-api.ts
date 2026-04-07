/**
 * Server-only fetch helper for unauthenticated public endpoints used by
 * SSR pages (landing, /areas/[area]). Intentionally separate from
 * src/lib/api.ts so it never imports the client auth wiring.
 *
 * Resolution order for the gateway URL:
 *   1. API_BASE_URL_SERVER  (server-only, e.g. in-cluster service DNS)
 *   2. NEXT_PUBLIC_API_BASE_URL (shared with client)
 */

export class ServerFetchError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ServerFetchError";
    this.status = status;
  }
}

function serverBaseUrl(): string {
  const u = process.env.API_BASE_URL_SERVER ?? process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!u) {
    throw new Error(
      "Neither API_BASE_URL_SERVER nor NEXT_PUBLIC_API_BASE_URL is set; cannot SSR public data",
    );
  }
  return u.replace(/\/+$/, "");
}

/**
 * Fetch JSON from a public gateway endpoint. Throws on non-2xx so callers
 * can wrap with try/catch and render an empty/error state.
 *
 * Uses Next's `revalidate` cache for short-TTL SSR caching.
 */
const SERVER_FETCH_TIMEOUT_MS = 8_000;

export async function serverGet<T>(path: string, revalidateSeconds = 60): Promise<T> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), SERVER_FETCH_TIMEOUT_MS);
  let res: Response;
  try {
    res = await fetch(`${serverBaseUrl()}${path}`, {
      headers: { Accept: "application/json" },
      next: { revalidate: revalidateSeconds },
      signal: ctrl.signal,
    });
  } catch (e) {
    if (e instanceof Error && e.name === "AbortError") {
      throw new ServerFetchError(
        504,
        `Upstream timeout after ${SERVER_FETCH_TIMEOUT_MS}ms for ${path}`,
      );
    }
    throw new ServerFetchError(
      502,
      e instanceof Error ? e.message : `Upstream fetch error for ${path}`,
    );
  } finally {
    clearTimeout(timer);
  }
  if (!res.ok) {
    throw new ServerFetchError(res.status, `${res.status} ${res.statusText} for ${path}`);
  }
  try {
    return (await res.json()) as T;
  } catch (parseErr) {
    // Wrap SyntaxError (or any body-read error) in ServerFetchError so
    // callers catching ServerFetchError receive a consistent error type
    // instead of a raw parse exception. Status 502 signals upstream
    // returned a malformed body.
    throw new ServerFetchError(
      502,
      `Malformed JSON from upstream for ${path}: ${
        parseErr instanceof Error ? parseErr.message : String(parseErr)
      }`,
    );
  }
}
