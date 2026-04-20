"use client";

// Reload the current URL rather than redirect to `/`. If the user was
// viewing a product page when the network dropped, Retry should bring
// them back to that same page — not wipe their context.
export function RetryButtonClient({ label }: { label: string }) {
  return (
    <button
      type="button"
      onClick={() => {
        if (typeof window !== "undefined") {
          window.location.reload();
        }
      }}
      className="min-h-[44px] min-w-[120px] rounded-[var(--radius-md)] bg-[var(--color-primary)] px-6 py-3 text-sm font-semibold text-white shadow-[var(--shadow-md)] transition hover:bg-[var(--color-primary-hover)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]"
    >
      {label}
    </button>
  );
}
