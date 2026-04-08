/**
 * Wallet pass API helpers.
 *
 * Backend endpoints (services/commerce-core/.../wallet/controller/WalletController.java):
 *   GET /v1/me/tickets/{ticketId}/apple-pass        -> application/vnd.apple.pkpass bytes
 *   GET /v1/me/tickets/{ticketId}/google-pass-link  -> { "saveUrl": "https://pay.google.com/..." }
 *
 * Both endpoints are owner-checked: cross-user access returns 404.
 */

import { ApiError, NetworkError, SignInRedirect, apiRequest, getCurrentIdToken } from "./api";

function baseUrl(): string {
  const u = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (!u) throw new Error("NEXT_PUBLIC_API_BASE_URL is not set");
  return u.replace(/\/+$/, "");
}

/** Fetches the signed .pkpass as a Blob. Caller is responsible for triggering the download. */
export async function downloadApplePass(ticketId: string): Promise<Blob> {
  const headers: Record<string, string> = { Accept: "application/vnd.apple.pkpass" };
  const token = await getCurrentIdToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  // 30s timeout, mirroring the payments-path timeout in src/lib/api.ts.
  // Without an explicit AbortController the fetch can hang indefinitely
  // on a wedged backend, leaving the spinner stuck.
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 30_000);
  let res: Response;
  try {
    res = await fetch(`${baseUrl()}/v1/me/tickets/${encodeURIComponent(ticketId)}/apple-pass`, {
      headers,
      signal: ctrl.signal,
    });
  } catch (e) {
    clearTimeout(timer);
    if (e instanceof Error && e.name === "AbortError") {
      throw new NetworkError("Apple Wallet download timed out");
    }
    throw new NetworkError(e instanceof Error ? e.message : "Network error");
  }
  clearTimeout(timer);
  if (res.status === 401) throw new SignInRedirect("/");
  if (!res.ok) {
    throw new ApiError(res.status, `${res.status} ${res.statusText}`);
  }
  return res.blob();
}

/** Fetches the Google Wallet save URL for the given ticket. */
export function getGoogleWalletUrl(ticketId: string): Promise<{ saveUrl: string }> {
  return apiRequest<{ saveUrl: string }>(
    `/v1/me/tickets/${encodeURIComponent(ticketId)}/google-pass-link`,
    { method: "GET" },
  );
}
