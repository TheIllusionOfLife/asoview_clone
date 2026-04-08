/**
 * ThemeToggle: verifies that the mounted-guard renders a hidden
 * placeholder before mount (so SSR markup is stable) and the real
 * button after mount, and that clicking it cycles light -> dark.
 */
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

const setThemeMock = vi.fn();
let currentTheme: string | undefined = "light";

vi.mock("next-themes", () => ({
  useTheme: () => ({ theme: currentTheme, setTheme: setThemeMock }),
}));

import { ThemeToggle } from "../ThemeToggle";

afterEach(() => {
  cleanup();
  setThemeMock.mockReset();
  currentTheme = "light";
});

describe("ThemeToggle", () => {
  it("renders a real toggle button after mount and cycles light -> dark on click", async () => {
    render(<ThemeToggle />);
    // After useEffect runs, the labelled button should appear.
    const btn = await waitFor(() => screen.getByLabelText(/toggle|テーマを切り替え/i));
    expect(btn).toBeTruthy();
    fireEvent.click(btn);
    expect(setThemeMock).toHaveBeenCalledWith("dark");
  });

  it("cycles dark -> system", async () => {
    currentTheme = "dark";
    render(<ThemeToggle />);
    const btn = await waitFor(() => screen.getByLabelText(/toggle|テーマを切り替え/i));
    fireEvent.click(btn);
    expect(setThemeMock).toHaveBeenCalledWith("system");
  });
});
