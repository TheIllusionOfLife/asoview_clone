// Broken fixture: parseFloat on a NUMERIC money string drops fractional yen.
export function subtotal(prices: string[]): number {
  return prices.map((p) => parseFloat(p)).reduce((a, b) => a + b, 0);
}
