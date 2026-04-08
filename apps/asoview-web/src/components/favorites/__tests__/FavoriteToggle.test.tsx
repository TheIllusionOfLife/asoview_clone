import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const addMock = vi.fn();
const removeMock = vi.fn();
vi.mock("@/lib/api", () => ({
  addFavorite: (...a: unknown[]) => addMock(...a),
  removeFavorite: (...a: unknown[]) => removeMock(...a),
  listFavorites: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ user: { uid: "u1" }, ready: true }),
}));
vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

import { FavoriteToggle } from "../FavoriteToggle";

afterEach(() => {
  cleanup();
  addMock.mockReset();
  removeMock.mockReset();
});

describe("FavoriteToggle", () => {
  it("optimistically toggles on click and calls addFavorite", async () => {
    addMock.mockResolvedValue(undefined);
    render(<FavoriteToggle productId="p1" />);
    const btn = screen.getByRole("button");
    expect(btn.getAttribute("aria-pressed")).toBe("false");
    fireEvent.click(btn);
    expect(btn.getAttribute("aria-pressed")).toBe("true");
    await waitFor(() => expect(addMock).toHaveBeenCalledWith("p1"));
  });

  it("reverts state when the API call fails", async () => {
    addMock.mockRejectedValue(new Error("net"));
    render(<FavoriteToggle productId="p2" />);
    const btn = screen.getByRole("button");
    await act(async () => {
      fireEvent.click(btn);
    });
    await waitFor(() => expect(btn.getAttribute("aria-pressed")).toBe("false"));
  });
});
