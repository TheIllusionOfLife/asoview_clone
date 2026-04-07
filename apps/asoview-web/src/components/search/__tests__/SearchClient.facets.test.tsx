// @vitest-environment jsdom
/**
 * Phase 3e commit 4: facet -> URL roundtrip regression.
 *
 * Renders `SearchClient` with an initial `?q=foo&category=outdoor`,
 * changes the category facet, and asserts `router.replace` is called
 * with the NEW category AND the preserved `q=foo`.
 */

import { fireEvent, render } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";

const { replaceMock, searchParamsRef } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  searchParamsRef: { current: new URLSearchParams("q=foo&category=outdoor") },
}));

vi.mock("@/i18n/navigation", () => ({
  useRouter: () => ({ replace: replaceMock, push: vi.fn() }),
  usePathname: () => "/search",
  Link: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => searchParamsRef.current,
}));

vi.mock("@/lib/api", () => ({
  searchProducts: vi.fn().mockResolvedValue({
    content: [],
    totalElements: 0,
    number: 0,
    size: 20,
  }),
  searchSuggest: vi.fn().mockResolvedValue({ suggestions: [] }),
}));

import { SearchClient } from "@/app/[locale]/search/SearchClient";

const messages = {
  search: {
    placeholder: "Search",
    noResults: "No results for '{q}'",
    error: "error",
    facets: {
      category: "Category",
      priceMin: "Min",
      priceMax: "Max",
      sort: "Sort",
      any: "Any",
      categories: { outdoor: "Outdoor", indoor: "Indoor", food: "Food", culture: "Culture" },
    },
    sort: { relevance: "Relevance", priceAsc: "Price asc", priceDesc: "Price desc" },
  },
};

describe("SearchClient facet URL roundtrip", () => {
  afterEach(() => {
    replaceMock.mockClear();
    searchParamsRef.current = new URLSearchParams("q=foo&category=outdoor");
  });

  it("changing category preserves q in the URL", () => {
    const { getByLabelText } = render(
      <NextIntlClientProvider locale="en" messages={messages}>
        <SearchClient
          initialQ="foo"
          initialCategory="outdoor"
          initialPriceMin=""
          initialPriceMax=""
          initialSort="relevance"
        />
      </NextIntlClientProvider>,
    );
    const select = getByLabelText("Category") as HTMLSelectElement;
    fireEvent.change(select, { target: { value: "indoor" } });

    expect(replaceMock).toHaveBeenCalledTimes(1);
    const url = replaceMock.mock.calls[0][0] as string;
    expect(url).toContain("q=foo");
    expect(url).toContain("category=indoor");
    expect(url).not.toContain("category=outdoor");
  });
});
