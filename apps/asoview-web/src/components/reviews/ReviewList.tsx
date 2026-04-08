"use client";

import { type ReviewResponse, listReviews } from "@/lib/api";
import { useFormatter } from "next-intl";
import { useEffect, useState } from "react";
import { HelpfulButton } from "./HelpfulButton";

const PAGE_SIZE = 10;

function Stars({ rating }: { rating: number }) {
  const r = Math.max(0, Math.min(5, Math.round(rating)));
  return (
    <span aria-label={`${r} / 5`} className="text-[var(--color-accent)]">
      {"★".repeat(r)}
      <span className="text-[var(--color-border)]">{"★".repeat(5 - r)}</span>
    </span>
  );
}

export function ReviewList({ productId }: { productId: string }) {
  const format = useFormatter();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<{
    content: ReviewResponse[];
    totalElements: number;
  } | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const ctrl = new AbortController();
    setErr(null);
    (async () => {
      try {
        const resp = await listReviews(productId, page, PAGE_SIZE, { signal: ctrl.signal });
        if (!cancelled) setData(resp);
      } catch (e) {
        if (!cancelled) setErr(e instanceof Error ? e.message : "failed");
      }
    })();
    return () => {
      cancelled = true;
      ctrl.abort();
    };
  }, [productId, page]);

  if (err) {
    return (
      <p role="alert" className="text-sm text-[var(--color-danger)]">
        {err}
      </p>
    );
  }
  if (!data) return <p className="text-sm text-[var(--color-ink-muted)]">…</p>;
  if (data.content.length === 0) {
    return <p className="text-sm text-[var(--color-ink-muted)]">まだレビューはありません。</p>;
  }
  const totalPages = Math.max(1, Math.ceil(data.totalElements / PAGE_SIZE));
  return (
    <div className="space-y-4">
      <ul className="space-y-4">
        {data.content.map((r) => (
          <li
            key={r.id}
            className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
          >
            <div className="flex items-center justify-between">
              <Stars rating={r.rating} />
              <time className="text-xs text-[var(--color-ink-muted)]">
                {format.dateTime(new Date(r.createdAt), { dateStyle: "medium" })}
              </time>
            </div>
            {r.title && <p className="mt-2 font-semibold text-sm">{r.title}</p>}
            {r.body && <p className="mt-1 whitespace-pre-line text-sm">{r.body}</p>}
            <div className="mt-3">
              <HelpfulButton reviewId={r.id} initialCount={r.helpfulCount} />
            </div>
          </li>
        ))}
      </ul>
      {totalPages > 1 && (
        <nav className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 disabled:opacity-40"
          >
            前へ
          </button>
          <span className="text-[var(--color-ink-muted)]">
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-[var(--radius-md)] border border-[var(--color-border)] px-3 py-1.5 disabled:opacity-40"
          >
            次へ
          </button>
        </nav>
      )}
    </div>
  );
}
