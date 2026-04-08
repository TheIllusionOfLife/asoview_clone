"use client";

import { ApiError, submitReview } from "@/lib/api";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";

/**
 * Minimal review-submission form. Shown on order detail after PAID.
 * Backend endpoint: POST /v1/reviews (NOT nested under productId).
 */
interface ReviewFormProps {
  productId: string;
  onSubmitted?: () => void;
}

export function ReviewForm({ productId, onSubmitted }: ReviewFormProps) {
  const t = useTranslations("reviews");
  const locale = useLocale();
  const [rating, setRating] = useState<number>(5);
  const [body, setBody] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  if (done) {
    return <p className="text-sm text-[var(--color-ink-muted)]">{t("submitted")}</p>;
  }

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setErr(null);
        setSubmitting(true);
        try {
          await submitReview({ productId, rating, body, language: locale });
          setDone(true);
          onSubmitted?.();
        } catch (e2) {
          setErr(e2 instanceof ApiError ? e2.message : t("submitError"));
        } finally {
          setSubmitting(false);
        }
      }}
      className="space-y-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-4"
    >
      <h3 className="font-semibold text-sm">{t("formTitle")}</h3>
      <label className="block text-xs">
        {t("rating")}
        <select
          value={rating}
          onChange={(e) => setRating(Number.parseInt(e.target.value, 10))}
          className="ml-2 rounded-[var(--radius-sm)] border border-[var(--color-border)] px-2 py-1"
        >
          {[5, 4, 3, 2, 1].map((n) => (
            <option key={n} value={n}>
              {n} ★
            </option>
          ))}
        </select>
      </label>
      <label className="block text-xs">
        {t("bodyLabel")}
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={4}
          placeholder={t("bodyPlaceholder")}
          className="mt-1 block w-full rounded-[var(--radius-sm)] border border-[var(--color-border)] px-2 py-1 text-sm"
        />
      </label>
      {err && (
        <p role="alert" className="text-xs text-[var(--color-danger)]">
          {err}
        </p>
      )}
      <button
        type="submit"
        disabled={submitting}
        className="rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
      >
        {submitting ? t("submitting") : t("submit")}
      </button>
    </form>
  );
}
