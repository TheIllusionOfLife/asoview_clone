// @vitest-environment jsdom
/**
 * Facet -> URL roundtrip regression.
 *
 * Renders `SearchClient` with initial `?q=foo&category=<uuidA>`, waits for
 * the /v1/categories/active fetch to resolve (so the slug-fallback-free
 * Facets select enables), switches to `<uuidB>`, and asserts
 * `router.replace` preserves `q=foo` while updating the category UUID.
 */

import { fireEvent, render, waitFor } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { afterEach, describe, expect, it, vi } from "vitest";

const uuidOutdoor = "ce61286b-0855-5726-b270-ef6079237eed";
const uuidIndoor = "fa1a1636-7474-542e-b925-7a8a6c8e50bb";

const { replaceMock, searchParamsRef, apiRequestMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  searchParamsRef: {
    current: new URLSearchParams("q=foo&category=ce61286b-0855-5726-b270-ef6079237eed"),
  },
  apiRequestMock: vi.fn(),
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
  apiRequest: apiRequestMock,
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
      loadingCategories: "Loading categories…",
      categoriesLoadError: "Could not load categories",
      retry: "Retry",
      categories: { outdoor: "Outdoor", indoor: "Indoor", food: "Food", culture: "Culture" },
    },
    sort: { relevance: "Relevance", priceAsc: "Price asc", priceDesc: "Price desc" },
  },
};

describe("SearchClient facet URL roundtrip", () => {
  afterEach(() => {
    replaceMock.mockClear();
    apiRequestMock.mockReset();
    searchParamsRef.current = new URLSearchParams(`q=foo&category=${uuidOutdoor}`);
  });

  it("changing category preserves q in the URL", async () => {
    apiRequestMock.mockResolvedValueOnce([
      { id: uuidOutdoor, name: "Outdoor" },
      { id: uuidIndoor, name: "Indoor" },
    ]);

    const { getByLabelText, getByRole } = render(
      <NextIntlClientProvider locale="en" messages={messages}>
        <SearchClient
          initialQ="foo"
          initialCategory={uuidOutdoor}
          initialPriceMin=""
          initialPriceMax=""
          initialSort="relevance"
        />
      </NextIntlClientProvider>,
    );

    await waitFor(() => {
      expect(getByRole("option", { name: "Indoor" })).toBeTruthy();
    });

    const select = getByLabelText("Category") as HTMLSelectElement;
    fireEvent.change(select, { target: { value: uuidIndoor } });

    expect(replaceMock).toHaveBeenCalledTimes(1);
    const url = replaceMock.mock.calls[0][0] as string;
    expect(url).toContain("q=foo");
    expect(url).toContain(`category=${uuidIndoor}`);
    expect(url).not.toContain(`category=${uuidOutdoor}`);
  });
});
