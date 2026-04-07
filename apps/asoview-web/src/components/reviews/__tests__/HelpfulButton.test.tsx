import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const voteHelpfulMock = vi.fn();
vi.mock("@/lib/api", () => ({
  voteHelpful: (...args: unknown[]) => voteHelpfulMock(...args),
}));

import { HelpfulButton } from "../HelpfulButton";

afterEach(() => {
  cleanup();
  voteHelpfulMock.mockReset();
});

describe("HelpfulButton", () => {
  it("increments optimistically and calls API once; second click is no-op", async () => {
    voteHelpfulMock.mockResolvedValue(undefined);
    render(<HelpfulButton reviewId="r1" initialCount={3} />);
    const btn = screen.getByRole("button");
    expect(btn.textContent).toContain("(3)");

    fireEvent.click(btn);
    expect(btn.textContent).toContain("(4)");
    await waitFor(() => expect(voteHelpfulMock).toHaveBeenCalledTimes(1));

    fireEvent.click(btn);
    fireEvent.click(btn);
    expect(voteHelpfulMock).toHaveBeenCalledTimes(1);
  });

  it("reverts on API failure", async () => {
    voteHelpfulMock.mockRejectedValue(new Error("boom"));
    render(<HelpfulButton reviewId="r2" initialCount={1} />);
    const btn = screen.getByRole("button");
    fireEvent.click(btn);
    await waitFor(() => expect(btn.textContent).toContain("(1)"));
  });
});
