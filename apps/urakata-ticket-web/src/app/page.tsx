import { redirect } from "next/navigation";

// Root is unreachable once middleware redirects to /{locale}; kept as a safety net
// for direct linking without the locale prefix.
export default function Root() {
  redirect("/ja");
}
