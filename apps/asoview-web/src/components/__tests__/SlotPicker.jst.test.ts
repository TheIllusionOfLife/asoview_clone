/**
 * Pitfall 8 (PR #22): backend stores slot dates as JST local strings (no
 * timezone). The frontend MUST compute "today" and the 14-day window in JST
 * — using `Intl.DateTimeFormat("sv-SE", { timeZone: "Asia/Tokyo" })` — not
 * via `getUTC*`/`setUTC*` against the user's local clock. Otherwise the
 * visible window shifts by up to 15 hours and stale-by-9-hours user can't
 * see today's slots.
 *
 * This test pins the system clock to 2026-04-07T23:30:00Z (08:30 JST on
 * 2026-04-08) and asserts:
 *   - todayIsoJst() returns "2026-04-08" (the JST date, not the UTC date),
 *   - addDaysIso treats the value as a calendar date (no DST drift across
 *     the boundary).
 */

import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { addDaysIso, todayIsoJst } from "../../lib/slot-date";

describe("SlotPicker JST date handling (Pitfall 8)", () => {
  beforeAll(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-04-07T23:30:00.000Z"));
  });

  afterAll(() => {
    vi.useRealTimers();
  });

  it("todayIsoJst returns the JST date, not the UTC date", () => {
    // 2026-04-07 23:30 UTC == 2026-04-08 08:30 JST
    expect(todayIsoJst()).toBe("2026-04-08");
  });

  it("addDaysIso advances by calendar date with no tz drift", () => {
    expect(addDaysIso("2026-04-08", 13)).toBe("2026-04-21");
    expect(addDaysIso("2026-04-08", -1)).toBe("2026-04-07");
  });
});
