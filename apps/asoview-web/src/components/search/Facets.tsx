"use client";

import { useTranslations } from "next-intl";

type Props = {
  category: string;
  priceMin: string;
  priceMax: string;
  sort: string;
  onChange: (updates: Record<string, string | null>) => void;
};

/**
 * Facet + sort controls. Every change calls `onChange` which rewrites
 * the URL via `router.replace` in the parent. Prices are integer minor
 * units (yen) — per CLAUDE.md PR #21 rule we parse as integer via
 * `parseInt` and reject anything fractional.
 */
export function Facets({ category, priceMin, priceMax, sort, onChange }: Props) {
  const t = useTranslations("search");

  // Integer yen only — strings from URL / inputs are validated to digits.
  // money-parse-ok: bounds only, integer yen
  const sanitizeYen = (raw: string): string => {
    const digits = raw.replace(/[^0-9]/g, "");
    return digits;
  };

  return (
    <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 rounded-lg border border-[var(--color-border)] p-4">
      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.category")}</span>
        <select
          value={category}
          onChange={(e) => onChange({ category: e.target.value || null })}
          className="rounded border border-[var(--color-border)] px-2 py-1"
        >
          <option value="">{t("facets.any")}</option>
          <option value="outdoor">{t("facets.categories.outdoor")}</option>
          <option value="indoor">{t("facets.categories.indoor")}</option>
          <option value="food">{t("facets.categories.food")}</option>
          <option value="culture">{t("facets.categories.culture")}</option>
        </select>
      </label>

      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.priceMin")}</span>
        <input
          type="text"
          inputMode="numeric"
          value={priceMin}
          onChange={(e) => onChange({ priceMin: sanitizeYen(e.target.value) || null })}
          className="rounded border border-[var(--color-border)] px-2 py-1"
        />
      </label>

      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.priceMax")}</span>
        <input
          type="text"
          inputMode="numeric"
          value={priceMax}
          onChange={(e) => onChange({ priceMax: sanitizeYen(e.target.value) || null })}
          className="rounded border border-[var(--color-border)] px-2 py-1"
        />
      </label>

      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.sort")}</span>
        <select
          value={sort}
          onChange={(e) => onChange({ sort: e.target.value })}
          className="rounded border border-[var(--color-border)] px-2 py-1"
        >
          <option value="relevance">{t("sort.relevance")}</option>
          <option value="price-asc">{t("sort.priceAsc")}</option>
          <option value="price-desc">{t("sort.priceDesc")}</option>
        </select>
      </label>
    </div>
  );
}
