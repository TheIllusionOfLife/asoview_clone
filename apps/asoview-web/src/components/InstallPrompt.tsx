"use client";

import { useTranslations } from "next-intl";
import { useEffect, useRef, useState } from "react";

// Install prompt UX for PWA installability.
//
// Two flows:
//   1. Android/Chrome: `beforeinstallprompt` is captured, banner is shown
//      on the second visit (localStorage visit-count >= 2), and tapping
//      Install dispatches the deferred prompt.
//   2. iOS Safari: no `beforeinstallprompt` event. Shown as an info
//      banner once, gated by UA detection AND only when capability
//      detection says we are not already installed.
//
// Capability detection runs FIRST: if the app is already standalone (via
// `matchMedia('(display-mode: standalone)').matches` or the legacy
// `navigator.standalone`), no banner renders. This filters installed
// iPadOS PWAs that would otherwise hit the UA fallback by mistake.
//
// The `beforeinstallprompt` listener is attached at MODULE LOAD, not in a
// useEffect, because browsers may fire it before React hydrates. Missing
// the event means the banner never shows on that session.

type DeferredPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

let deferredPrompt: DeferredPromptEvent | null = null;
const subscribers = new Set<(e: DeferredPromptEvent | null) => void>();

if (typeof window !== "undefined") {
  window.addEventListener("beforeinstallprompt", (event) => {
    event.preventDefault();
    deferredPrompt = event as DeferredPromptEvent;
    for (const fn of subscribers) fn(deferredPrompt);
  });
  window.addEventListener("appinstalled", () => {
    deferredPrompt = null;
    try {
      localStorage.setItem("pwa:install-outcome", "accepted");
    } catch {
      // ignore storage errors (Safari private mode)
    }
    for (const fn of subscribers) fn(null);
  });
}

function isStandalone(): boolean {
  if (typeof window === "undefined") return false;
  if (window.matchMedia?.("(display-mode: standalone)").matches) return true;
  // iOS Safari legacy flag
  const nav = navigator as Navigator & { standalone?: boolean };
  return nav.standalone === true;
}

function isIosSafari(): boolean {
  if (typeof navigator === "undefined") return false;
  const ua = navigator.userAgent;
  const isIDevice = /iPhone|iPad|iPod/i.test(ua);
  // Chrome on iOS contains "CriOS"; Firefox "FxiOS"; Edge "EdgiOS".
  const isNonSafariBrowser = /CriOS|FxiOS|EdgiOS/i.test(ua);
  return isIDevice && !isNonSafariBrowser;
}

function readOutcome(): string | null {
  try {
    return localStorage.getItem("pwa:install-outcome");
  } catch {
    return null;
  }
}

function incrementVisitCount(): number {
  try {
    const n = Number(localStorage.getItem("pwa:visit-count") ?? "0") + 1;
    localStorage.setItem("pwa:visit-count", String(n));
    return n;
  } catch {
    return 1;
  }
}

function writeOutcome(value: string): void {
  try {
    localStorage.setItem("pwa:install-outcome", value);
  } catch {
    // ignore
  }
}

type Mode = "none" | "android" | "ios";

export function InstallPrompt() {
  const t = useTranslations("pwa");
  const [mode, setMode] = useState<Mode>("none");
  const [hasDeferred, setHasDeferred] = useState<boolean>(deferredPrompt !== null);
  const visitsRef = useRef<number | null>(null);

  useEffect(() => {
    const onChange = (e: DeferredPromptEvent | null) => setHasDeferred(e !== null);
    subscribers.add(onChange);
    return () => {
      subscribers.delete(onChange);
    };
  }, []);

  // Bump the visit counter exactly once per mount — NOT inside the
  // `[hasDeferred]` effect below, or `beforeinstallprompt` firing after
  // hydration would re-run the effect and double-count, triggering the
  // banner on the very first visit.
  useEffect(() => {
    if (isStandalone()) return;
    if (readOutcome()) return;
    visitsRef.current = incrementVisitCount();
  }, []);

  useEffect(() => {
    if (isStandalone()) return;
    if (readOutcome()) return;
    const visits = visitsRef.current ?? 0;
    if (hasDeferred && visits >= 2) {
      setMode("android");
      return;
    }
    // Match the Android second-visit contract for iOS Safari too — a
    // first-visit popover is noisy and the ADR / runbook document the
    // same threshold for both platforms.
    if (!hasDeferred && visits >= 2 && isIosSafari()) {
      setMode("ios");
    }
  }, [hasDeferred]);

  if (mode === "none") return null;

  if (mode === "android") {
    return (
      <Banner
        title={t("install.title")}
        body={t("install.body")}
        ctaLabel={t("install.cta")}
        dismissLabel={t("install.dismiss")}
        onCta={async () => {
          const prompt = deferredPrompt;
          if (!prompt) {
            setMode("none");
            return;
          }
          try {
            await prompt.prompt();
            const choice = await prompt.userChoice;
            writeOutcome(choice.outcome);
          } catch {
            writeOutcome("dismissed");
          } finally {
            deferredPrompt = null;
            setMode("none");
          }
        }}
        onDismiss={() => {
          writeOutcome("dismissed");
          setMode("none");
        }}
      />
    );
  }

  // iOS info-only banner.
  return (
    <Banner
      title={t("iosInstall.title")}
      body={t("iosInstall.body")}
      ctaLabel={null}
      dismissLabel={t("iosInstall.dismiss")}
      onCta={null}
      onDismiss={() => {
        writeOutcome("ios-info-shown");
        setMode("none");
      }}
    />
  );
}

type BannerProps = {
  title: string;
  body: string;
  ctaLabel: string | null;
  dismissLabel: string;
  onCta: (() => void) | null;
  onDismiss: () => void;
};

function Banner({ title, body, ctaLabel, dismissLabel, onCta, onDismiss }: BannerProps) {
  return (
    <section
      aria-live="polite"
      aria-label={title}
      className="fixed inset-x-2 bottom-2 z-40 mx-auto max-w-md rounded-[var(--radius-md)] border border-[var(--color-primary)]/40 bg-[var(--color-surface)] px-4 py-3 shadow-[var(--shadow-md)]"
    >
      <h2 className="text-sm font-semibold text-[var(--color-primary)]">{title}</h2>
      <p className="mt-1 text-xs leading-relaxed text-[color:var(--color-foreground,#1a2238)]">
        {body}
      </p>
      <div className="mt-3 flex items-center justify-end gap-2">
        <button
          type="button"
          onClick={onDismiss}
          className="min-h-[44px] rounded-[var(--radius-md)] px-4 py-2 text-xs font-medium text-[color:var(--color-foreground,#1a2238)] hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]"
        >
          {dismissLabel}
        </button>
        {ctaLabel && onCta ? (
          <button
            type="button"
            onClick={onCta}
            className="min-h-[44px] rounded-[var(--radius-md)] bg-[var(--color-primary)] px-4 py-2 text-xs font-semibold text-white shadow-[var(--shadow-md)] transition hover:bg-[var(--color-primary-hover)] focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-primary)]"
          >
            {ctaLabel}
          </button>
        ) : null}
      </div>
    </section>
  );
}
