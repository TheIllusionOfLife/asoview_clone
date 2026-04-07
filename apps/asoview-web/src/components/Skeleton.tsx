type SkeletonProps = {
  className?: string;
  /** Accessible label for screen readers; defaults to "読み込み中". */
  label?: string;
};

/**
 * Generic shimmer placeholder. Use for any async fetch to prevent CLS.
 * Width/height come from the caller via Tailwind classes.
 */
export function Skeleton({ className = "", label = "読み込み中" }: SkeletonProps) {
  return (
    <div
      role="status"
      aria-label={label}
      aria-live="polite"
      className={`animate-pulse rounded-[var(--radius-md)] bg-[var(--color-border)]/60 ${className}`}
    />
  );
}
