import type { AreaResponse } from "@/lib/types";
import Link from "next/link";

export function AreaCard({ area }: { area: AreaResponse }) {
  return (
    <Link
      href={`/areas/${area.slug}`}
      className="group block rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-5 text-center shadow-[var(--shadow-sm)] hover:border-[var(--color-primary)] hover:shadow-[var(--shadow-md)] transition"
    >
      <span className="font-display text-lg font-semibold group-hover:text-[var(--color-primary)]">
        {area.name}
      </span>
    </Link>
  );
}
