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

  let res: Response;
  try {
    res = await fetch(`${baseUrl()}/v1/me/tickets/${encodeURIComponent(ticketId)}/apple-pass`, {
      headers,
    });
  } catch (e) {
    throw new NetworkError(e instanceof Error ? e.message : "Network error");
  }
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
