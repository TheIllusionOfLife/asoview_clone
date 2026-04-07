"use client";

/**
 * React binding over the cart store. Single hook used by Header badge,
 * /cart page, and the SlotPicker "add to cart" path. On signin (uid
 * appears) the guest cart is merged into the user cart exactly once.
 */

import { useCallback, useEffect, useState, useSyncExternalStore } from "react";
import { useAuth } from "./auth";
import {
  type Cart,
  type CartLine,
  addLine,
  emptyCart,
  mergeGuestIntoUser,
  readCart,
  removeLine,
  subscribeCart,
  subtotal,
  updateQuantity,
  writeCart,
} from "./cart";

export function useCart() {
  const { user, ready } = useAuth();
  const uid = user?.uid ?? null;
  const [hydrated, setHydrated] = useState(false);

  // useSyncExternalStore: re-render whenever the cart for the current
  // uid changes. Server snapshot is always emptyCart() so SSR matches
  // first paint, then we hydrate from localStorage in the effect.
  const cart = useSyncExternalStore<Cart>(
    subscribeCart,
    () => (hydrated ? readCart(uid) : emptyCart()),
    () => emptyCart(),
  );

  useEffect(() => {
    if (!ready) return;
    if (uid) {
      mergeGuestIntoUser(uid);
    }
    setHydrated(true);
  }, [ready, uid]);

  const add = useCallback(
    (line: CartLine) => {
      const next = addLine(readCart(uid), line);
      writeCart(uid, next);
    },
    [uid],
  );

  const setQty = useCallback(
    (slotId: string, qty: number) => {
      const next = updateQuantity(readCart(uid), slotId, qty);
      writeCart(uid, next);
    },
    [uid],
  );

  const remove = useCallback(
    (slotId: string) => {
      const next = removeLine(readCart(uid), slotId);
      writeCart(uid, next);
    },
    [uid],
  );

  return {
    cart,
    hydrated,
    subtotal: subtotal(cart),
    add,
    setQty,
    remove,
  };
}
