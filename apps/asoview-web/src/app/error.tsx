"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Surface for client observability later (Sentry, etc.)
    console.error("[asoview-web] route error", error);
  }, [error]);

  return (
    <div className="mx-auto max-w-xl px-4 py-24 text-center">
      <p className="font-display text-7xl text-[var(--color-primary)]">!</p>
      <h1 className="font-display text-3xl mt-4">問題が発生しました</h1>
      <p className="mt-3 text-[var(--color-ink-muted)]">
        ページの読み込み中にエラーが発生しました。時間をおいてもう一度お試しください。
      </p>
      {error.digest && (
        <p className="mt-2 text-xs text-[var(--color-ink-muted)]">エラーID: {error.digest}</p>
      )}
      <button
        type="button"
        onClick={reset}
        className="mt-6 inline-flex items-center rounded-[var(--radius-md)] bg-[var(--color-primary)] px-5 py-2.5 text-white font-medium hover:bg-[var(--color-primary-hover)]"
      >
        再試行
      </button>
    </div>
  );
}
