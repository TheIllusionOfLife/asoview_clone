import { Link } from "@/i18n/navigation";
import { CartBadge } from "./CartBadge";
import { PointsBalance } from "./points/PointsBalance";

export function Header() {
  return (
    <header className="border-b border-[var(--color-border)] bg-[var(--color-surface)]">
      <div className="mx-auto max-w-6xl px-4 h-16 flex items-center justify-between">
        <Link href="/" className="font-display text-2xl font-bold text-[var(--color-primary)]">
          asoview!
        </Link>
        <nav className="flex items-center gap-6 text-sm">
          <Link href="/" className="hover:text-[var(--color-primary)]">
            ホーム
          </Link>
          <Link href="/search" className="hover:text-[var(--color-primary)]">
            検索
          </Link>
          <Link href="/cart" className="hover:text-[var(--color-primary)] inline-flex items-center">
            カート
            <CartBadge />
          </Link>
          <Link href="/me/favorites" className="hover:text-[var(--color-primary)]">
            お気に入り
          </Link>
          <Link
            href="/me/points"
            className="hover:text-[var(--color-primary)] inline-flex items-center gap-1"
          >
            ポイント
            <PointsBalance />
          </Link>
          <Link href="/me/orders" className="hover:text-[var(--color-primary)]">
            予約履歴
          </Link>
        </nav>
      </div>
    </header>
  );
}
