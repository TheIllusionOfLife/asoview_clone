"use client";

import { type AutosuggestResponse, searchSuggest } from "@/lib/api";
import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

type Props = {
  value: string;
  onSubmit: (q: string) => void;
};

/**
 * Search input with a 250ms-debounced autosuggest dropdown backed by
 * `GET /v1/search/suggest`. Debounce uses a plain setTimeout/clearTimeout —
 * no lodash. On submit the parent updates the URL, which is the source
 * of truth for `value`.
 */
export function SearchBox({ value, onSubmit }: Props) {
  const t = useTranslations("search");
  const [draft, setDraft] = useState(value);
  const [suggestions, setSuggestions] = useState<AutosuggestResponse["suggestions"]>([]);
  const [open, setOpen] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const blurTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Resync from parent (URL) when user navigates back/forward.
  useEffect(() => {
    setDraft(value);
  }, [value]);

  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    const q = draft.trim();
    if (q.length < 2) {
      setSuggestions([]);
      return;
    }
    let cancelled = false;
    timerRef.current = setTimeout(async () => {
      try {
        const r = await searchSuggest(q);
        if (cancelled) return;
        setSuggestions(r.suggestions ?? []);
      } catch {
        if (cancelled) return;
        setSuggestions([]);
      }
    }, 250);
    return () => {
      cancelled = true;
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, [draft]);

  // Clear any pending blur-close timer on unmount.
  useEffect(() => {
    return () => {
      if (blurTimerRef.current) clearTimeout(blurTimerRef.current);
    };
  }, []);

  return (
    <form
      className="relative"
      onSubmit={(e) => {
        e.preventDefault();
        setOpen(false);
        onSubmit(draft.trim());
      }}
    >
      <input
        type="search"
        value={draft}
        onChange={(e) => {
          setDraft(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => {
          if (blurTimerRef.current) clearTimeout(blurTimerRef.current);
          blurTimerRef.current = setTimeout(() => setOpen(false), 150);
        }}
        placeholder={t("placeholder")}
        aria-label={t("placeholder")}
        className="w-full rounded-lg border border-[var(--color-border)] px-4 py-3 text-base focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]"
      />
      {open && suggestions.length > 0 && (
        <ul className="absolute z-20 mt-1 w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] shadow-lg">
          {suggestions.map((s) => (
            <li key={s.productId}>
              <button
                type="button"
                className="block w-full px-4 py-2 text-left hover:bg-[var(--color-surface-hover)]"
                onMouseDown={(e) => {
                  // mousedown beats the input blur → onSubmit fires before dropdown closes
                  e.preventDefault();
                  setDraft(s.name);
                  setOpen(false);
                  onSubmit(s.name);
                }}
              >
                {s.name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </form>
  );
}
