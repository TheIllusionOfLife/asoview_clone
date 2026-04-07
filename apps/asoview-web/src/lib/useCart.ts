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
  invalidateSnapshotCache,
  mergeGuestIntoUser,
  readCart,
  removeLine,
  snapshotCart,
  subscribeCart,
  subtotal,
  updateQuantity,
  writeCart,
} from "./cart";

const SERVER_EMPTY_CART: Cart = emptyCart();
function getServerSnapshot(): Cart {
  return SERVER_EMPTY_CART;
}

export function useCart() {
  const { user, ready } = useAuth();
  const uid = user?.uid ?? null;
  const [hydrated, setHydrated] = useState(false);

  // useSyncExternalStore requires referential stability when the
  // underlying store has not changed. `snapshotCart(uid)` reads through
  // a module-level cache keyed on uid; the cache is invalidated inside
  // the cart store's `notify()` so two consecutive calls without a
  // mutation return the SAME object reference. Returning a fresh
  // `readCart(uid)` here would tear the store and cause infinite
  // re-renders.
  const cart = useSyncExternalStore<Cart>(
    subscribeCart,
    () => (hydrated ? snapshotCart(uid) : SERVER_EMPTY_CART),
    getServerSnapshot,
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
