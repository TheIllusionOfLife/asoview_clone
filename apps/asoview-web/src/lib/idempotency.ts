/**
 * Idempotency-Key management for `POST /v1/orders`.
 *
 * The contract: a UUID v4 is minted per *draft fingerprint*
 * (`productId+slotId+quantity`). The same fingerprint reuses the same
 * UUID for the lifetime of the session, so a refresh mid-network-call
 * does NOT double-book the slot. When the order reaches a terminal state
 * (PAID / CANCELLED / FAILED) the key is cleared so a subsequent
 * "book again" mints a fresh one.
 *
 * Storage: sessionStorage (not localStorage) — survives refresh, dies
 * with the tab. Keyed by a stable string derived from the fingerprint.
 */

const STORAGE_PREFIX = "asoview:idem:";

export type IdempotencyFingerprint = {
  productId: string;
  slotId: string;
  quantity: number;
};

export function fingerprintKey(fp: IdempotencyFingerprint): string {
  return `${STORAGE_PREFIX}${fp.productId}|${fp.slotId}|${fp.quantity}`;
}

function uuidV4(): string {
  // Prefer Web Crypto when available (browsers + Node 19+).
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // RFC 4122 fallback using Math.random — only used in environments
  // without Web Crypto (e.g. very old test runners).
  const rnd = (n: number) =>
    Array.from({ length: n }, () => Math.floor(Math.random() * 16).toString(16)).join("");
  const y = "89ab"[Math.floor(Math.random() * 4)];
  return `${rnd(8)}-${rnd(4)}-4${rnd(3)}-${y}${rnd(3)}-${rnd(12)}`;
}

type Storage = {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
};

function defaultStorage(): Storage | null {
  if (typeof sessionStorage === "undefined") return null;
  return sessionStorage;
}

/**
 * Get or mint the Idempotency-Key for a draft fingerprint. Same input
 * returns the same key for the lifetime of the session.
 */
export function getOrCreateIdempotencyKey(
  fp: IdempotencyFingerprint,
  storage: Storage | null = defaultStorage(),
): string {
  if (!storage) return uuidV4();
  const key = fingerprintKey(fp);
  const existing = storage.getItem(key);
  if (existing) return existing;
  const minted = uuidV4();
  storage.setItem(key, minted);
  return minted;
}

/** Clear the idempotency key for a fingerprint after terminal state. */
export function clearIdempotencyKey(
  fp: IdempotencyFingerprint,
  storage: Storage | null = defaultStorage(),
): void {
  if (!storage) return;
  storage.removeItem(fingerprintKey(fp));
}
