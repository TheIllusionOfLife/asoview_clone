import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const getBalanceMock = vi.fn();
vi.mock("@/lib/api", () => ({
  getPointsBalance: (...a: unknown[]) => getBalanceMock(...a),
}));

const useAuthMock = vi.fn();
vi.mock("@/lib/auth", () => ({
  useAuth: () => useAuthMock(),
}));

import { PointsBalance } from "../PointsBalance";

afterEach(() => {
  cleanup();
  getBalanceMock.mockReset();
  useAuthMock.mockReset();
});

describe("PointsBalance", () => {
  it("renders balance when signed in and fetch resolves", async () => {
    useAuthMock.mockReturnValue({ ready: true, user: { uid: "u1" } });
    getBalanceMock.mockResolvedValue({ balance: 1234 });
    const { container } = render(<PointsBalance />);
    await waitFor(() => {
      expect(screen.getByLabelText("保有ポイント").textContent).toContain("1234");
    });
    expect(container.firstChild).not.toBeNull();
  });

  it("renders nothing when signed out", () => {
    useAuthMock.mockReturnValue({ ready: true, user: null });
    const { container } = render(<PointsBalance />);
    expect(container.firstChild).toBeNull();
    expect(getBalanceMock).not.toHaveBeenCalled();
  });
});
