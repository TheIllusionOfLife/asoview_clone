import { beforeEach, describe, expect, it } from "vitest";
import { clearIdempotencyKey, fingerprintKey, getOrCreateIdempotencyKey } from "../idempotency";

function memoryStorage() {
  const data = new Map<string, string>();
  return {
    getItem: (k: string) => data.get(k) ?? null,
    setItem: (k: string, v: string) => {
      data.set(k, v);
    },
    removeItem: (k: string) => {
      data.delete(k);
    },
    _data: data,
  };
}

describe("idempotency", () => {
  let store: ReturnType<typeof memoryStorage>;
  const fp = { productId: "p1", slotId: "s1", quantity: 2 };

  beforeEach(() => {
    store = memoryStorage();
  });

  it("derives a stable storage key from the fingerprint", () => {
    expect(fingerprintKey(fp)).toBe("asoview:idem:p1|s1|2");
  });

  it("mints a new key on first call", () => {
    const k = getOrCreateIdempotencyKey(fp, store);
    expect(k).toMatch(/^[0-9a-f-]{36}$/i);
    expect(store._data.size).toBe(1);
  });

  it("returns the same key for the same fingerprint", () => {
    const a = getOrCreateIdempotencyKey(fp, store);
    const b = getOrCreateIdempotencyKey(fp, store);
    expect(a).toBe(b);
  });

  it("returns a different key for a different fingerprint", () => {
    const a = getOrCreateIdempotencyKey(fp, store);
    const b = getOrCreateIdempotencyKey({ ...fp, quantity: 3 }, store);
    expect(a).not.toBe(b);
  });

  it("clears the key on terminal state", () => {
    const a = getOrCreateIdempotencyKey(fp, store);
    clearIdempotencyKey(fp, store);
    const b = getOrCreateIdempotencyKey(fp, store);
    expect(a).not.toBe(b);
  });

  it("falls back to a fresh UUID when storage is unavailable", () => {
    const a = getOrCreateIdempotencyKey(fp, null);
    const b = getOrCreateIdempotencyKey(fp, null);
    expect(a).not.toBe(b);
  });
});
