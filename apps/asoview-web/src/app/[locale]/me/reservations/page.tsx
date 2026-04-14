import { Suspense } from "react";
import { MyReservationsClient } from "./MyReservationsClient";

export const dynamic = "force-dynamic";

export default function MyReservationsPage() {
  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <h1 className="font-display text-3xl font-bold">予約リクエスト</h1>
      <Suspense
        fallback={<p className="mt-6 text-sm text-[var(--color-ink-muted)]">読み込み中…</p>}
      >
        <MyReservationsClient />
      </Suspense>
    </div>
  );
}
