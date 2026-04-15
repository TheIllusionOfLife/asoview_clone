import { z } from "zod";
import { randomUUID } from "./device";
import { apiBaseUrl, firebaseAuth } from "./firebase";

// Must match backend QrCodeGenerator (16 hex chars, case-insensitive).
const QR_FORMAT = /^TKT-[0-9A-Fa-f]{16}$/;

export const RedeemOutcome = z.enum([
  "REDEEMED",
  "ALREADY_USED",
  "EXPIRED",
  "REVOKED",
  "ENTITLEMENT_NOT_ACTIVE",
  "OUTSIDE_VALIDITY_WINDOW",
  "TICKET_NOT_SCANNABLE",
  "RATE_LIMITED",
]);
export type RedeemOutcome = z.infer<typeof RedeemOutcome>;

const RedeemSuccessSchema = z.object({
  outcome: RedeemOutcome,
  passId: z.string().optional(),
  usedAt: z.string().optional(),
  replayed: z.boolean().optional(),
});

const ProblemSchema = z.object({
  code: z.string(),
  detail: z.string().optional(),
});

export type RedeemResult =
  | { kind: "ok"; outcome: RedeemOutcome; usedAt?: string; replayed?: boolean }
  | { kind: "denied"; code: string; detail?: string; status: number }
  | { kind: "network_error"; message: string };

export function isValidQrFormat(s: string): boolean {
  return QR_FORMAT.test(s);
}

/**
 * Redeem a scanned QR. Retries ONLY on 5xx (exponential backoff, max 3 attempts). Never retries
 * on 4xx — terminal outcomes must not accidentally exhaust the server-side per-pass rate limit.
 * Offline / network error is surfaced as a discriminated `network_error` so the UI can show
 * "connection required" instead of a generic failure.
 */
export async function redeem(
  qr: string,
  scannerDeviceId: string,
  venueId: string,
): Promise<RedeemResult> {
  if (!isValidQrFormat(qr)) {
    return { kind: "denied", code: "FORMAT_INVALID", status: 400 };
  }

  const auth = firebaseAuth();
  const user = auth.currentUser;
  if (!user) {
    return { kind: "denied", code: "UNAUTHENTICATED", status: 401 };
  }
  const idempotencyKey = randomUUID();
  const body = JSON.stringify({ qrCodePayload: qr, scannerDeviceId, venueId });
  const url = `${apiBaseUrl()}/v1/op/tickets/redeem`;

  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      // getIdToken can reject (expired refresh token, offline). Inside the try so
      // callers always get a typed RedeemResult rather than an unhandled rejection.
      const idToken = await user.getIdToken();
      const res = await fetch(url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${idToken}`,
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey,
        },
        body,
      });
      if (res.status >= 500) {
        if (attempt < 2) {
          await new Promise((r) => setTimeout(r, 400 * 2 ** attempt));
          continue;
        }
        return { kind: "denied", code: "SERVER_ERROR", status: res.status };
      }
      const json = await res.json().catch(() => null);
      if (res.ok && json) {
        const parsed = RedeemSuccessSchema.safeParse(json);
        if (parsed.success) {
          return {
            kind: "ok",
            outcome: parsed.data.outcome,
            usedAt: parsed.data.usedAt,
            replayed: parsed.data.replayed,
          };
        }
      }
      const problem = ProblemSchema.safeParse(json);
      return {
        kind: "denied",
        code: problem.success ? problem.data.code : "UNKNOWN",
        detail: problem.success ? problem.data.detail : undefined,
        status: res.status,
      };
    } catch (e) {
      if (attempt < 2) {
        await new Promise((r) => setTimeout(r, 400 * 2 ** attempt));
        continue;
      }
      return {
        kind: "network_error",
        message: e instanceof Error ? e.message : "network error",
      };
    }
  }
  return { kind: "network_error", message: "exhausted retries" };
}
