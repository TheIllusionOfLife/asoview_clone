import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const downloadMock = vi.fn();
vi.mock("@/lib/wallet", () => ({
  downloadApplePass: (...a: unknown[]) => downloadMock(...a),
  getGoogleWalletUrl: vi.fn(),
}));

import { AppleWalletButton } from "../AppleWalletButton";

const labels = {
  add: "Add to Apple Wallet",
  unavailableFrom: "Available from {date}",
  downloading: "Downloading...",
  error: "Could not add to wallet.",
};

afterEach(() => {
  cleanup();
  downloadMock.mockReset();
});

describe("AppleWalletButton validity gating", () => {
  it("active phase: enabled, clicking downloads the pass", async () => {
    downloadMock.mockResolvedValue(new Blob(["pkpass-bytes"]));
    // jsdom lacks URL.createObjectURL / revokeObjectURL
    const createObjectURL = vi.fn(() => "blob:fake");
    const revokeObjectURL = vi.fn();
    // biome-ignore lint/suspicious/noExplicitAny: jsdom shim
    (URL as any).createObjectURL = createObjectURL;
    // biome-ignore lint/suspicious/noExplicitAny: jsdom shim
    (URL as any).revokeObjectURL = revokeObjectURL;

    render(<AppleWalletButton ticketId="t-1" phase="active" labels={labels} />);
    const btn = screen.getByRole("button", { name: /Add to Apple Wallet/ });
    expect((btn as HTMLButtonElement).disabled).toBe(false);
    fireEvent.click(btn);
    await waitFor(() => {
      expect(downloadMock).toHaveBeenCalledWith("t-1");
    });
    expect(createObjectURL).toHaveBeenCalled();
  });

  it("before phase: disabled with validFrom label", () => {
    render(
      <AppleWalletButton
        ticketId="t-1"
        phase="before"
        validFromLabel="2030-01-01"
        labels={labels}
      />,
    );
    const btn = screen.getByRole("button", { name: /Add to Apple Wallet/ });
    expect((btn as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/Available from 2030-01-01/)).toBeDefined();
    expect(downloadMock).not.toHaveBeenCalled();
  });

  it("expired phase: renders nothing", () => {
    const { container } = render(
      <AppleWalletButton ticketId="t-1" phase="expired" labels={labels} />,
    );
    expect(container.firstChild).toBeNull();
  });
});
