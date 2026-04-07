export function Footer() {
  return (
    <footer className="border-t border-[var(--color-border)] bg-[var(--color-surface)] mt-16">
      <div className="mx-auto max-w-6xl px-4 py-8 text-sm text-[var(--color-ink-muted)] flex flex-col sm:flex-row justify-between gap-2">
        <p>© {new Date().getFullYear()} asoview! clone. Study project.</p>
        <p className="font-display">体験を、もっと身近に。</p>
      </div>
    </footer>
  );
}
