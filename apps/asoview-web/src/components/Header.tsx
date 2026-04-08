import { Link } from "@/i18n/navigation";
import { useTranslations } from "next-intl";
import { CartBadge } from "./CartBadge";
import { ThemeToggle } from "./ThemeToggle";
import { PointsBalance } from "./points/PointsBalance";

export function Header() {
  const t = useTranslations("nav");
  const tCommon = useTranslations("common");
  return (
    <header className="border-b border-[var(--color-border)] bg-[var(--color-surface)]">
      <div className="mx-auto max-w-6xl px-4 h-16 flex items-center justify-between">
        <Link href="/" className="font-display text-2xl font-bold text-[var(--color-primary)]">
          {tCommon("siteName")}
        </Link>
        <nav className="flex items-center gap-6 text-sm">
          <Link href="/" className="hover:text-[var(--color-primary)]">
            {t("home")}
          </Link>
          <Link href="/search" className="hover:text-[var(--color-primary)]">
            {tCommon("search")}
          </Link>
          <Link href="/cart" className="hover:text-[var(--color-primary)] inline-flex items-center">
            {t("cart")}
            <CartBadge />
          </Link>
          <Link href="/me/favorites" className="hover:text-[var(--color-primary)]">
            {t("favorites")}
          </Link>
          <Link
            href="/me/points"
            className="hover:text-[var(--color-primary)] inline-flex items-center gap-1"
          >
            {t("points")}
            <PointsBalance />
          </Link>
          <Link href="/me/orders" className="hover:text-[var(--color-primary)]">
            {t("orders")}
          </Link>
          <Link href="/signin" className="hover:text-[var(--color-primary)]">
            {t("signIn")}
          </Link>
          <ThemeToggle />
        </nav>
      </div>
    </header>
  );
}
