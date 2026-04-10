import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const serverGetMock = vi.fn();
vi.mock("@/lib/server-api", () => ({
  serverGet: (...a: unknown[]) => serverGetMock(...a),
  ServerFetchError: class ServerFetchError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

// ProductCard imports next-intl navigation; stub it to a plain anchor.
vi.mock("@/components/ProductCard", () => ({
  ProductCard: ({ product }: { product: { id: string; title: string } }) => (
    <div data-testid={`card-${product.id}`}>{product.title}</div>
  ),
}));

import { SimilarProducts } from "../SimilarProducts";

afterEach(() => {
  cleanup();
  serverGetMock.mockReset();
});

function page(items: Array<{ id: string; title: string }>) {
  return {
    content: items.map((i) => ({
      id: i.id,
      title: i.title,
      description: null,
      status: "ACTIVE",
      categoryId: "c1",
      venueId: null,
      variants: [],
    })),
    totalElements: items.length,
    number: 0,
    size: items.length,
  };
}

describe("SimilarProducts", () => {
  it("renders cards and excludes the current product", async () => {
    serverGetMock.mockResolvedValue(
      page([
        { id: "p1", title: "Alpha" },
        { id: "p2", title: "Beta" },
        { id: "p3", title: "Gamma" },
      ]),
    );
    const element = await SimilarProducts({
      title: "Similar",
      categoryId: "c1",
      excludeProductId: "p2",
      limit: 8,
    });
    render(element as React.ReactElement);

    expect(screen.getByTestId("card-p1")).toBeDefined();
    expect(screen.queryByTestId("card-p2")).toBeNull();
    expect(screen.getByTestId("card-p3")).toBeDefined();
    // categoryId is forwarded to the catalog list endpoint.
    expect(serverGetMock).toHaveBeenCalledWith(expect.stringContaining("categoryId=c1"));
  });

  it("renders null when the backend returns empty", async () => {
    serverGetMock.mockResolvedValue(page([]));
    const element = await SimilarProducts({ title: "Popular" });
    expect(element).toBeNull();
  });
});
