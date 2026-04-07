/**
 * Locale-aware navigation wrappers. Import `Link`, `redirect`, `usePathname`,
 * `useRouter`, and `getPathname` from here instead of `next/link` /
 * `next/navigation` so every internal link automatically carries the
 * active locale prefix (and switches between locales correctly).
 */
import { createNavigation } from "next-intl/navigation";
import { routing } from "./routing";

export const { Link, redirect, usePathname, useRouter, getPathname } = createNavigation(routing);
