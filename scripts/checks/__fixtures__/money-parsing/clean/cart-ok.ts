// Clean fixture: money parsed via parseMinorUnits.
export function subtotal(prices: string[]): number {
  return prices.map(parseMinorUnits).reduce((a, b) => a + b, 0);
}
declare function parseMinorUnits(s: string): number;
