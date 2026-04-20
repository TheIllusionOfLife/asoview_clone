"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { apiRequest } from "@/lib/api";

type CategoryOption = { id: string; name: string };

type Props = {
  category: string;
  priceMin: string;
  priceMax: string;
  sort: string;
  onChange: (updates: Record<string, string | null>) => void;
};

type LoadState = "loading" | "ready" | "error";

/**
 * Facet + sort controls. Every change calls `onChange` which rewrites
 * the URL via `router.replace` in the parent. Prices are integer minor
 * units (yen) — per CLAUDE.md PR #21 rule we parse as integer via
 * `parseInt` and reject anything fractional.
 *
 * The category `<option value>` is always a UUID fetched from
 * `/v1/categories/active`. A slug fallback was removed because
 * Vertex AI Search stores `categoryId` as a UUID; if a user picked a
 * slug value before the fetch resolved, the URL would capture the
 * slug and every downstream search silently returned zero hits.
 */
export function Facets({ category, priceMin, priceMax, sort, onChange }: Props) {
  const t = useTranslations("search");
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  // Generation counter + mount guard so concurrent in-flight fetches (e.g.
  // user hammers the retry button) don't overwrite a newer result, and a
  // fetch that resolves after unmount doesn't call setState on a dead tree.
  const fetchGeneration = useRef(0);
  const mountedRef = useRef(true);

  const fetchCategories = useCallback(async () => {
    const myGen = ++fetchGeneration.current;
    setLoadState("loading");
    try {
      const data = await apiRequest<CategoryOption[]>("/v1/categories/active", {
        method: "GET",
        retries: 1,
      });
      if (!mountedRef.current || myGen !== fetchGeneration.current) return;
      setCategories(data);
      setLoadState("ready");
    } catch (err) {
      if (!mountedRef.current || myGen !== fetchGeneration.current) return;
      console.warn("Failed to load active categories", err);
      setLoadState("error");
    }
  }, []);

  useEffect(() => {
    mountedRef.current = true;
    void fetchCategories();
    return () => {
      mountedRef.current = false;
    };
  }, [fetchCategories]);

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
          disabled={loadState !== "ready"}
          aria-busy={loadState === "loading"}
          className="min-h-[44px] rounded border border-[var(--color-border)] px-2 py-1 disabled:opacity-60"
        >
          {loadState === "loading" && <option value="">{t("facets.loadingCategories")}</option>}
          {loadState === "error" && <option value="">{t("facets.categoriesLoadError")}</option>}
          {loadState === "ready" && (
            <>
              <option value="">{t("facets.any")}</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))}
            </>
          )}
        </select>
        {loadState === "error" && (
          <button
            type="button"
            onClick={() => {
              void fetchCategories();
            }}
            className="mt-1 min-h-[44px] px-2 text-xs text-[var(--color-accent)] underline"
          >
            {t("facets.retry")}
          </button>
        )}
      </label>

      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.priceMin")}</span>
        <input
          type="text"
          inputMode="numeric"
          value={priceMin}
          onChange={(e) => onChange({ priceMin: sanitizeYen(e.target.value) || null })}
          className="min-h-[44px] rounded border border-[var(--color-border)] px-2 py-1"
        />
      </label>

      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.priceMax")}</span>
        <input
          type="text"
          inputMode="numeric"
          value={priceMax}
          onChange={(e) => onChange({ priceMax: sanitizeYen(e.target.value) || null })}
          className="min-h-[44px] rounded border border-[var(--color-border)] px-2 py-1"
        />
      </label>

      <label className="flex flex-col text-sm">
        <span className="mb-1 text-[var(--color-ink-muted)]">{t("facets.sort")}</span>
        <select
          value={sort}
          onChange={(e) => onChange({ sort: e.target.value })}
          className="min-h-[44px] rounded border border-[var(--color-border)] px-2 py-1"
        >
          <option value="relevance">{t("sort.relevance")}</option>
          <option value="price_asc">{t("sort.priceAsc")}</option>
          <option value="price_desc">{t("sort.priceDesc")}</option>
        </select>
      </label>
    </div>
  );
}
