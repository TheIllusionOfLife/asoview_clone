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
export async function serverGet<T>(path: string, revalidateSeconds = 60): Promise<T> {
  const res = await fetch(`${serverBaseUrl()}${path}`, {
    headers: { Accept: "application/json" },
    next: { revalidate: revalidateSeconds },
  });
  if (!res.ok) {
    throw new ServerFetchError(res.status, `${res.status} ${res.statusText} for ${path}`);
  }
  return (await res.json()) as T;
}
