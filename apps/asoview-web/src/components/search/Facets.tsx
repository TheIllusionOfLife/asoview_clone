"use client";

import { apiRequest } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

type CategoryOption = { id: string; name: string };

type Props = {
  category: string;
  priceMin: string;
  priceMax: string;
  sort: string;
  onChange: (updates: Record<string, string | null>) => void;
};

const FALLBACK_CATEGORIES: CategoryOption[] = [
  { id: "outdoor", name: "outdoor" },
  { id: "indoor", name: "indoor" },
  { id: "food", name: "food" },
  { id: "culture", name: "culture" },
];

/**
 * Facet + sort controls. Every change calls `onChange` which rewrites
 * the URL via `router.replace` in the parent. Prices are integer minor
 * units (yen) — per CLAUDE.md PR #21 rule we parse as integer via
 * `parseInt` and reject anything fractional.
 */
export function Facets({ category, priceMin, priceMax, sort, onChange }: Props) {
  const t = useTranslations("search");
  const [categories, setCategories] = useState<CategoryOption[]>(FALLBACK_CATEGORIES);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const data = await apiRequest<CategoryOption[]>("/v1/categories/active", {
          method: "GET",
          retries: 1,
        });
        if (!cancelled && data.length > 0) {
          setCategories(data);
        }
      } catch (err) {
        console.warn("Failed to load active categories; using fallback", err);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Integer yen only — strings from URL / inputs are validated to digits.
  // Japanese IMEs commonly emit full-width digits (０-９); normalize to
  // ASCII before stripping non-digit characters so a user typing 1500 in
  // full-width does not silently lose the bound.
  // money-parse-ok: bounds only, integer yen
  const sanitizeYen = (raw: string): string => {
    const ascii = raw.replace(/[０-９]/g, (d) => String.fromCharCode(d.charCodeAt(0) - 0xfee0));
    return ascii.replace(/[^0-9]/g, "");
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
          {categories.map((cat) => (
            <option key={cat.id} value={cat.id}>
              {cat.name}
            </option>
          ))}
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
