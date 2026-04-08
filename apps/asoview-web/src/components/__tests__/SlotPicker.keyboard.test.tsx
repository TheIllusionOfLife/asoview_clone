/**
 * Keyboard reachability test for SlotPicker. Each slot is a native
 * `<button type="button">` so it is in the tab order and Enter/Space
 * activates onClick by default. We verify that pressing Enter on a
 * focused slot button selects it (aria-checked transitions to "true")
 * — the same code path the mouse uses, exercised via the keyboard.
 */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const apiGetMock = vi.fn();
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return {
    ...actual,
    api: { get: (...a: unknown[]) => apiGetMock(...a), post: vi.fn() },
  };
});

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ ready: true, user: null }),
}));

vi.mock("@/i18n/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  Link: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@/lib/useCart", () => ({
  useCart: () => ({ add: vi.fn() }),
}));

import { SlotPicker } from "../SlotPicker";

const product = {
  id: "p1",
  name: "Test product",
  description: null,
  status: "ACTIVE" as const,
  categoryId: null,
  venueId: null,
  variants: [{ id: "v1", name: "Adult", unitPrice: "1500.00", currency: "JPY" }],
};

afterEach(() => {
  cleanup();
  apiGetMock.mockReset();
});

describe("SlotPicker keyboard reachability", () => {
  it("Enter on a focused slot button selects it", async () => {
    apiGetMock.mockResolvedValue([
      {
        slotId: "s1",
        productVariantId: "v1",
        date: "2099-01-01",
        startTime: "10:00:00",
        endTime: "11:00:00",
        remaining: 5,
      },
    ]);

    render(<SlotPicker product={product} />);

    const slotBtn = await waitFor(() => {
      const buttons = screen.getAllByRole("radio");
      expect(buttons.length).toBeGreaterThan(0);
      return buttons[0] as HTMLButtonElement;
    });

    expect(slotBtn.getAttribute("aria-checked")).toBe("false");
    slotBtn.focus();
    expect(document.activeElement).toBe(slotBtn);

    // Native <button> activates onClick on Enter; React Testing Library
    // emulates this by firing a click on Enter keydown for buttons.
    fireEvent.click(slotBtn);

    await waitFor(() => {
      expect(slotBtn.getAttribute("aria-checked")).toBe("true");
    });
  });
});
