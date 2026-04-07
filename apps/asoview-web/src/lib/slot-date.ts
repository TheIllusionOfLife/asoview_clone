/**
 * JST date helpers extracted from `SlotPicker.tsx` so they can be unit-tested
 * without dragging the whole React tree (and the `@/lib/api` import path) into
 * the Vitest module graph.
 *
 * Pitfall 8 (PR #22): backend stores slot dates as JST local strings; client
 * must compute "today" and the 14-day window in JST regardless of the
 * browser's local timezone.
 */

export const JST_TIMEZONE = "Asia/Tokyo";

export function todayIsoJst(): string {
  // sv-SE produces "YYYY-MM-DD" directly.
  return new Intl.DateTimeFormat("sv-SE", { timeZone: JST_TIMEZONE }).format(new Date());
}

export function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  // Use UTC arithmetic purely as a calendar library — we're treating the ISO
  // string as an abstract date, not a moment in time, so daylight-saving and
  // tz drift do not apply (JST has no DST).
  const base = new Date(Date.UTC(y, m - 1, d));
  base.setUTCDate(base.getUTCDate() + days);
  const yy = base.getUTCFullYear();
  const mm = String(base.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(base.getUTCDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}
