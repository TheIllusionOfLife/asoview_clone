/**
 * Persistent user-scoped cart for asoview-web.
 *
 * Storage: localStorage (survives tab close, unlike the idempotency key
 * which is sessionStorage). Keyed by Firebase uid so two users on the
 * same device do NOT see each other's cart. Unauthenticated users use
 * the special `guest` namespace.
 *
 * On sign-in, `mergeGuestIntoUser` runs once: any line in the guest
 * cart is merged into the signed-in user's cart, deduped by `slotId`
 * (the cart line's quantity wins). The guest cart is cleared after.
 *
 * The store is intentionally a thin module of pure functions + a
 * subscribable in-memory mirror so React components can re-render via
 * `useSyncExternalStore`. No useState gymnastics, no context.
 */

const STORAGE_PREFIX = "asoview:cart:";
export const GUEST_CART_KEY = `${STORAGE_PREFIX}guest`;

export type CartLine = {
  productId: string;
  productVariantId: string;
  slotId: string;
  /** ISO datetime, e.g. "2026-04-12T10:00:00". */
  slotStartAt: string;
  slotEndAt: string;
  quantity: number;
  /** NUMERIC string from backend, e.g. "1500.00". */
  unitPrice: string;
  productSnapshot: {
    name: string;
    area?: string | null;
  };
};

export type Cart = {
  lines: CartLine[];
};

export function emptyCart(): Cart {
  return { lines: [] };
}

export function cartKey(uid: string | null): string {
  return uid ? `${STORAGE_PREFIX}${uid}` : GUEST_CART_KEY;
}

type Storage = Pick<globalThis.Storage, "getItem" | "setItem" | "removeItem">;

function defaultStorage(): Storage | null {
  if (typeof localStorage === "undefined") return null;
  return localStorage;
}

function isValidCartLine(value: unknown): value is CartLine {
  if (!value || typeof value !== "object") return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.productId === "string" &&
    typeof v.productVariantId === "string" &&
    typeof v.slotId === "string" &&
    typeof v.slotStartAt === "string" &&
    typeof v.slotEndAt === "string" &&
    typeof v.quantity === "number" &&
    Number.isFinite(v.quantity) &&
    v.quantity >= 1 &&
    typeof v.unitPrice === "string" &&
    !!v.productSnapshot &&
    typeof (v.productSnapshot as Record<string, unknown>).name === "string"
  );
}

export function readCart(uid: string | null, storage: Storage | null = defaultStorage()): Cart {
  if (!storage) return emptyCart();
  try {
    const raw = storage.getItem(cartKey(uid));
    if (!raw) return emptyCart();
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object") return emptyCart();
    const lines = (parsed as { lines?: unknown }).lines;
    if (!Array.isArray(lines)) return emptyCart();
    // Filter out malformed lines rather than discarding the whole cart
    // on first corruption. Malformed entries can appear after a manual
    // localStorage edit, a cross-origin tampered copy, or a schema
    // drift from a previous version.
    const valid = lines.filter(isValidCartLine);
    return { lines: valid };
  } catch {
    return emptyCart();
  }
}

export function writeCart(
  uid: string | null,
  cart: Cart,
  storage: Storage | null = defaultStorage(),
): void {
  if (!storage) return;
  storage.setItem(cartKey(uid), JSON.stringify(cart));
  invalidateSnapshotCache(uid);
  notify();
}

export function clearCart(uid: string | null, storage: Storage | null = defaultStorage()): void {
  if (!storage) return;
  storage.removeItem(cartKey(uid));
  invalidateSnapshotCache(uid);
  notify();
}

/**
 * Add a line to a cart. If a line for the same `slotId` already exists,
 * the quantity is replaced (NOT incremented). Spec: "prefer cart line's
 * quantity" — i.e. the latest add wins. This matches the user model where
 * the slot picker shows a quantity selector and the user expects "set
 * the quantity to N" semantics, not "add N to whatever was there."
 */
export function addLine(cart: Cart, line: CartLine): Cart {
  const filtered = cart.lines.filter((l) => l.slotId !== line.slotId);
  return { lines: [...filtered, line] };
}

export function updateQuantity(cart: Cart, slotId: string, quantity: number): Cart {
  const q = Math.max(1, Math.floor(quantity));
  return {
    lines: cart.lines.map((l) => (l.slotId === slotId ? { ...l, quantity: q } : l)),
  };
}

export function removeLine(cart: Cart, slotId: string): Cart {
  return { lines: cart.lines.filter((l) => l.slotId !== slotId) };
}

export function parseMinorUnits(s: string): number {
  const m = /^(\d+)(?:\.(\d{1,2}))?$/.exec(s);
  if (!m) return 0;
  const yen = Number.parseInt(m[1], 10);
  const sen = m[2] ? Number.parseInt(m[2].padEnd(2, "0"), 10) : 0;
  return yen * 100 + sen;
}

export function subtotal(cart: Cart): string {
  let totalMinor = 0;
  for (const l of cart.lines) {
    totalMinor += parseMinorUnits(l.unitPrice) * l.quantity;
  }
  const yen = Math.trunc(totalMinor / 100);
  const sen = totalMinor % 100;
  return sen === 0 ? `${yen}.00` : `${yen}.${sen.toString().padStart(2, "0")}`;
}

/**
 * Merge the guest cart into the signed-in user's cart. Dedup by slotId,
 * preferring the cart line's (i.e. guest's) quantity. Idempotent: clears
 * the guest cart afterwards so a re-mount doesn't double-merge.
 *
 * Returns the resulting user cart for callers that want to use it
 * immediately without a second read.
 */
export function mergeGuestIntoUser(uid: string, storage: Storage | null = defaultStorage()): Cart {
  if (!storage) return emptyCart();
  const guest = readCart(null, storage);
  const user = readCart(uid, storage);
  if (guest.lines.length === 0) return user;
  let merged = user;
  for (const line of guest.lines) {
    merged = addLine(merged, line);
  }
  writeCart(uid, merged, storage);
  clearCart(null, storage);
  return merged;
}

// ---------- Snapshot cache (for useSyncExternalStore stability) ----------

/**
 * Module-level cache of the most recent parsed Cart per uid. The cache
 * exists so that `useSyncExternalStore`'s `getSnapshot` returns the
 * same object reference when nothing has changed; otherwise React
 * detects a "store has changed" on every render and tears.
 *
 * Invalidation: every cart mutation (`writeCart`, `clearCart`) calls
 * `invalidateSnapshotCache` BEFORE notifying listeners, so the next
 * `snapshotCart(uid)` call re-parses storage and the new (stable)
 * reference is returned.
 */
const snapshotCache = new Map<string | null, Cart>();

export function invalidateSnapshotCache(uid: string | null): void {
  snapshotCache.delete(uid);
}

export function snapshotCart(uid: string | null, storage: Storage | null = defaultStorage()): Cart {
  const cached = snapshotCache.get(uid);
  if (cached) return cached;
  const fresh = readCart(uid, storage);
  snapshotCache.set(uid, fresh);
  return fresh;
}

// ---------- Subscription mirror for React ----------

const listeners = new Set<() => void>();

function notify(): void {
  for (const l of listeners) l();
}

export function subscribeCart(listener: () => void): () => void {
  listeners.add(listener);
  if (typeof window !== "undefined") {
    const onStorage = (e: StorageEvent) => {
      if (!e.key?.startsWith(STORAGE_PREFIX)) return;
      // Cross-tab write: invalidate the snapshot cache BEFORE notifying
      // the listener so the subsequent `snapshotCart(uid)` re-reads
      // localStorage and returns a fresh (stable) reference. Without
      // this, React's useSyncExternalStore sees the same cached object
      // reference, skips re-rendering, and cross-tab updates are
      // silently dropped. (Devin PR #22 finding.)
      //
      // The snapshot cache keys the guest cart on the literal `null`,
      // but the storage key is `asoview:cart:guest`. Strip the prefix
      // and map "guest" (or empty) back to null so the cache entry we
      // invalidate matches the one snapshotCart read from.
      const suffix = e.key.slice(STORAGE_PREFIX.length);
      const uid = suffix && suffix !== "guest" ? suffix : null;
      invalidateSnapshotCache(uid);
      listener();
    };
    window.addEventListener("storage", onStorage);
    return () => {
      listeners.delete(listener);
      window.removeEventListener("storage", onStorage);
    };
  }
  return () => {
    listeners.delete(listener);
  };
}
