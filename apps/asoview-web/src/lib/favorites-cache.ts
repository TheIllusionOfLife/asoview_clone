/**
 * Singleton in-memory favorites cache.
 *
 * Solves the N+1 of every <FavoriteToggle> on a search/grid page calling
 * GET /v1/me/favorites individually. The first subscriber triggers a single
 * fetch; concurrent subscribers reuse the in-flight Promise; the resolved
 * Set is shared across all consumers and updated optimistically by
 * `markFavorited` / `markUnfavorited`.
 *
 * Listeners are notified after every state mutation so React components
 * using `useFavorited(productId)` re-render when the cache changes.
 *
 * Auth lifecycle: callers should call `resetFavoritesCache()` on sign-out
 * so the next signed-in user does not see the previous user's favorites.
 */

import { listFavorites } from "./api";

type State =
  | { kind: "idle" }
  | { kind: "loading"; promise: Promise<void> }
  | { kind: "ready"; ids: Set<string> }
  | { kind: "error" };

let state: State = { kind: "idle" };
let epoch = 0;
const listeners = new Set<() => void>();

function notify() {
  for (const l of listeners) l();
}

export function subscribeFavorites(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function resetFavoritesCache(): void {
  epoch += 1;
  state = { kind: "idle" };
  notify();
}

/**
 * Ensure the cache is loaded. Returns a Promise that resolves once the
 * cache reaches "ready" or "error". Idempotent: concurrent calls share
 * the same in-flight Promise.
 *
 * Failure mode: on error, state is "error" and `isFavorited` returns
 * false (fail closed). The toggle still works to add favorites; the user
 * just won't see the heart filled in until next reload.
 */
export function ensureFavoritesLoaded(): Promise<void> {
  if (state.kind === "ready" || state.kind === "error") return Promise.resolve();
  if (state.kind === "loading") return state.promise;
  const capturedEpoch = epoch;
  const promise = (async () => {
    try {
      const ids = await listFavorites();
      if (epoch !== capturedEpoch) return;
      state = { kind: "ready", ids: new Set(ids) };
    } catch {
      if (epoch !== capturedEpoch) return;
      state = { kind: "error" };
    } finally {
      if (epoch === capturedEpoch) notify();
    }
  })();
  state = { kind: "loading", promise };
  return promise;
}

export function isFavorited(productId: string): boolean {
  if (state.kind !== "ready") return false;
  return state.ids.has(productId);
}

export function markFavorited(productId: string): void {
  if (state.kind !== "ready") return;
  if (state.ids.has(productId)) return;
  const next = new Set(state.ids);
  next.add(productId);
  state = { kind: "ready", ids: next };
  notify();
}

export function markUnfavorited(productId: string): void {
  if (state.kind !== "ready") return;
  if (!state.ids.has(productId)) return;
  const next = new Set(state.ids);
  next.delete(productId);
  state = { kind: "ready", ids: next };
  notify();
}

/** Test-only: seed the cache directly. */
export function __seedFavoritesCacheForTest(ids: string[] | null): void {
  if (ids === null) {
    state = { kind: "idle" };
  } else {
    state = { kind: "ready", ids: new Set(ids) };
  }
  notify();
}
