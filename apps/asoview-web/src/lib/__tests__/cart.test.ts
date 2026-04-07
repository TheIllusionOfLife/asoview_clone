import { beforeEach, describe, expect, it } from "vitest";
import {
  type CartLine,
  GUEST_CART_KEY,
  addLine,
  cartKey,
  emptyCart,
  mergeGuestIntoUser,
  readCart,
  removeLine,
  subtotal,
  updateQuantity,
  writeCart,
} from "../cart";

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

function makeLine(slotId: string, qty = 1, price = "1500.00"): CartLine {
  return {
    productId: `prod-${slotId}`,
    productVariantId: `var-${slotId}`,
    slotId,
    slotStartAt: "2026-04-12T10:00:00",
    slotEndAt: "2026-04-12T11:00:00",
    quantity: qty,
    unitPrice: price,
    productSnapshot: { name: `Product ${slotId}` },
  };
}

describe("cart key namespacing", () => {
  it("uses guest namespace when uid is null", () => {
    expect(cartKey(null)).toBe(GUEST_CART_KEY);
  });
  it("namespaces by uid when present", () => {
    expect(cartKey("user-1")).toBe("asoview:cart:user-1");
    expect(cartKey("user-1")).not.toBe(cartKey("user-2"));
  });
});

describe("cart pure helpers", () => {
  it("addLine appends a new line", () => {
    const out = addLine(emptyCart(), makeLine("s1"));
    expect(out.lines).toHaveLength(1);
  });

  it("addLine replaces existing line for the same slotId (latest wins)", () => {
    const c1 = addLine(emptyCart(), makeLine("s1", 1));
    const c2 = addLine(c1, makeLine("s1", 4));
    expect(c2.lines).toHaveLength(1);
    expect(c2.lines[0]?.quantity).toBe(4);
  });

  it("updateQuantity floors at 1", () => {
    const c = addLine(emptyCart(), makeLine("s1", 3));
    expect(updateQuantity(c, "s1", 0).lines[0]?.quantity).toBe(1);
    expect(updateQuantity(c, "s1", 5).lines[0]?.quantity).toBe(5);
  });

  it("removeLine drops the matching slot", () => {
    let c = addLine(emptyCart(), makeLine("s1"));
    c = addLine(c, makeLine("s2"));
    expect(removeLine(c, "s1").lines).toHaveLength(1);
    expect(removeLine(c, "s1").lines[0]?.slotId).toBe("s2");
  });

  it("subtotal preserves fractional yen via minor-units accumulation", () => {
    let c = emptyCart();
    c = addLine(c, makeLine("s1", 2, "1500.00"));
    c = addLine(c, makeLine("s2", 1, "800.50"));
    // 1500*2 + 800.50 = 3800.50
    expect(subtotal(c)).toBe("3800.50");
  });

  it("subtotal handles single-digit fractional yen", () => {
    let c = emptyCart();
    c = addLine(c, makeLine("s1", 1, "1.99"));
    c = addLine(c, makeLine("s2", 1, "0.01"));
    expect(subtotal(c)).toBe("2.00");
  });

  it("subtotal handles integer-only price strings", () => {
    let c = emptyCart();
    c = addLine(c, makeLine("s1", 3, "100"));
    expect(subtotal(c)).toBe("300.00");
  });

  it("subtotal of empty cart is zero string", () => {
    expect(subtotal(emptyCart())).toBe("0.00");
  });

  it("subtotal accumulates mixed line items", () => {
    let c = emptyCart();
    c = addLine(c, makeLine("s1", 2, "1500.50"));
    c = addLine(c, makeLine("s2", 3, "999.99"));
    // 1500.50*2 + 999.99*3 = 3001.00 + 2999.97 = 6000.97
    expect(subtotal(c)).toBe("6000.97");
  });
});

describe("cart persistence", () => {
  let store: ReturnType<typeof memoryStorage>;
  beforeEach(() => {
    store = memoryStorage();
  });

  it("returns empty cart when storage is empty", () => {
    expect(readCart("u1", store).lines).toEqual([]);
  });

  it("round-trips a cart", () => {
    const c = addLine(emptyCart(), makeLine("s1", 3));
    writeCart("u1", c, store);
    expect(readCart("u1", store).lines[0]?.quantity).toBe(3);
  });

  it("isolates cart by uid", () => {
    writeCart("u1", addLine(emptyCart(), makeLine("s1")), store);
    expect(readCart("u2", store).lines).toEqual([]);
  });

  it("returns empty cart on malformed JSON", () => {
    store.setItem(cartKey("u1"), "{not json");
    expect(readCart("u1", store)).toEqual(emptyCart());
  });
});

describe("mergeGuestIntoUser", () => {
  let store: ReturnType<typeof memoryStorage>;
  beforeEach(() => {
    store = memoryStorage();
  });

  it("no-ops when guest is empty", () => {
    writeCart("u1", addLine(emptyCart(), makeLine("s1", 2)), store);
    const merged = mergeGuestIntoUser("u1", store);
    expect(merged.lines).toHaveLength(1);
  });

  it("merges guest lines into user cart and clears guest", () => {
    writeCart(null, addLine(emptyCart(), makeLine("s1", 3)), store);
    writeCart("u1", addLine(emptyCart(), makeLine("s2", 1)), store);
    const merged = mergeGuestIntoUser("u1", store);
    expect(merged.lines.map((l) => l.slotId).sort()).toEqual(["s1", "s2"]);
    expect(readCart(null, store).lines).toEqual([]);
  });

  it("dedupes by slotId, preferring guest (cart) quantity", () => {
    writeCart(null, addLine(emptyCart(), makeLine("s1", 4)), store);
    writeCart("u1", addLine(emptyCart(), makeLine("s1", 1)), store);
    const merged = mergeGuestIntoUser("u1", store);
    expect(merged.lines).toHaveLength(1);
    expect(merged.lines[0]?.quantity).toBe(4);
  });
});
