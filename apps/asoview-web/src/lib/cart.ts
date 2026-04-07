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

export function readCart(uid: string | null, storage: Storage | null = defaultStorage()): Cart {
  if (!storage) return emptyCart();
  try {
    const raw = storage.getItem(cartKey(uid));
    if (!raw) return emptyCart();
    const parsed = JSON.parse(raw) as Cart;
    if (!parsed || !Array.isArray(parsed.lines)) return emptyCart();
    return parsed;
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
  notify();
}

export function clearCart(uid: string | null, storage: Storage | null = defaultStorage()): void {
  if (!storage) return;
  storage.removeItem(cartKey(uid));
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

export function subtotal(cart: Cart): number {
  let sum = 0;
  for (const l of cart.lines) {
    const n = Number(l.unitPrice);
    if (Number.isFinite(n)) sum += Math.trunc(n) * l.quantity;
  }
  return sum;
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

// ---------- Subscription mirror for React ----------

const listeners = new Set<() => void>();

function notify(): void {
  for (const l of listeners) l();
}

export function subscribeCart(listener: () => void): () => void {
  listeners.add(listener);
  if (typeof window !== "undefined") {
    const onStorage = (e: StorageEvent) => {
      if (e.key?.startsWith(STORAGE_PREFIX)) listener();
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
