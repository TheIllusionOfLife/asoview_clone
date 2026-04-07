/**
 * Pitfall 9 (PR #22): `cart.subtotal` parses NUMERIC money strings via the
 * minor-units integer path. Any regression to `parseFloat` or `Math.trunc` on
 * the wrong side of the conversion silently produces wrong totals on
 * fractional yen.
 *
 * The matrix below covers: whole yen, fractional yen, the 99→100 rounding
 * boundary, multi-line addition into 100, "100" without a decimal part,
 * and zero. If anyone reverts the integer-minor-units path, at least one of
 * these cases fails on exact-string equality.
 */

import { describe, expect, it } from "vitest";
import { type CartLine, subtotal } from "../cart";

function line(unitPrice: string, quantity = 1): CartLine {
  return {
    productId: "p",
    productVariantId: "v",
    slotId: `slot-${unitPrice}-${quantity}`,
    slotStartAt: "2026-04-12T10:00:00",
    slotEndAt: "2026-04-12T11:00:00",
    quantity,
    unitPrice,
    productSnapshot: { name: "Test" },
  };
}

describe("cart.subtotal — fractional-yen matrix (Pitfall 9)", () => {
  it.each<[string, string[], string]>([
    ["whole yen", ["1500.00"], "1500.00"],
    ["fractional yen", ["1500.50"], "1500.50"],
    ["sub-yen", ["0.99"], "0.99"],
    ["addition rounds to whole", ["99.99", "0.01"], "100.00"],
    ["no decimal part", ["100"], "100.00"],
    ["zero", ["0"], "0.00"],
  ])("%s: %j → %s", (_label, prices, expected) => {
    const cart = { lines: prices.map((p) => line(p)) };
    expect(subtotal(cart)).toBe(expected);
  });

  it("multi-quantity multiplication", () => {
    expect(subtotal({ lines: [line("1500.50", 3)] })).toBe("4501.50");
  });
});
