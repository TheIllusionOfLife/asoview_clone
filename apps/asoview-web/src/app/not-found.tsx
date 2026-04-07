import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <p className="font-display text-7xl text-[var(--color-primary)]">404</p>
      <h1 className="font-display text-3xl mt-4">ページが見つかりません</h1>
      <p className="mt-3 text-[var(--color-ink-muted)]">
        お探しのページは移動または削除された可能性があります。
      </p>
      <Link
        href="/"
        className="mt-6 inline-flex items-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-white font-medium hover:bg-[var(--color-primary-hover)]"
      >
        ホームに戻る
      </Link>
    </div>
  );
}
