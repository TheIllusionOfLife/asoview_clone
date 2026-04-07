/**
 * Sanitize a `?next=` redirect parameter from a sign-in flow.
 *
 * Returns the input only if it is a same-origin relative path:
 *   - non-empty
 *   - starts with `/`
 *   - does NOT start with `//` (protocol-relative URL)
 *   - does NOT start with `/\` or contain `\` (Windows-style path injection)
 *   - is NOT an absolute URL (`http://`, `https://`, `javascript:`, etc.)
 *
 * Anything else is dropped and `/` is returned. The goal is to make it
 * impossible for an attacker-controlled `?next=` to bounce a freshly
 * signed-in user to an off-origin destination.
 */
export function sanitizeNext(next: string | null | undefined): string {
  if (next == null) return "/";
  if (typeof next !== "string") return "/";
  if (next.length === 0) return "/";
  if (!next.startsWith("/")) return "/";
  if (next.startsWith("//")) return "/";
  if (next.includes("\\")) return "/";
  // Defensive: reject anything that parses as an absolute URL.
  // A leading `/` already excludes most schemes, but `/javascript:` etc
  // would still be relative — we accept those because they have no
  // navigation effect (browser treats them as same-origin paths).
  return next;
}
