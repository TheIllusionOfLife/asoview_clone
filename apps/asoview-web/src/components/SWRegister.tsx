"use client";

import { useEffect } from "react";

// Registers the narrow opt-in service worker. Production-only; dev mode
// would clash with HMR and Next.js App Router's in-dev error overlay.
// Registration failures are surfaced as a console warning rather than
// thrown — a broken SW must not break first-render.
export function SWRegister() {
  useEffect(() => {
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;
    if (process.env.NODE_ENV !== "production") return;
    navigator.serviceWorker.register("/sw.js", { scope: "/" }).catch((err) => {
      console.warn("SW registration failed", err);
    });
  }, []);
  return null;
}
