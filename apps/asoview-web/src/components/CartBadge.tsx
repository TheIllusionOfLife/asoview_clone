"use client";

import { useCart } from "@/lib/useCart";

export function CartBadge() {
  const { cart, hydrated } = useCart();
  if (!hydrated) return null;
  const count = cart.lines.reduce((n, l) => n + l.quantity, 0);
  if (count === 0) return null;
  return (
    <span
      aria-label={`${count}件カートに入っています`}
      aria-live="polite"
      className="ml-1 inline-flex items-center justify-center min-w-5 h-5 px-1 rounded-full bg-[var(--color-primary)] text-white text-[11px] font-semibold"
    >
      {count}
    </span>
  );
}
