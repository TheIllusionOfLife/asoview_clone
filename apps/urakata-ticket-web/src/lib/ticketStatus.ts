import type { TicketPass } from "./api";

type Translator = (key: "valid" | "used" | "expired" | "revoked") => string;

export function statusBadgeClass(status: TicketPass["status"]): string {
  switch (status) {
    case "VALID":
      return "bg-[var(--color-success)] text-white";
    case "USED":
      return "bg-[var(--color-text-muted)] text-white";
    case "EXPIRED":
      return "bg-[var(--color-warning)] text-white";
    case "REVOKED":
      return "bg-[var(--color-danger)] text-white";
  }
}

export function statusLabel(status: TicketPass["status"], t: Translator): string {
  switch (status) {
    case "VALID":
      return t("valid");
    case "USED":
      return t("used");
    case "EXPIRED":
      return t("expired");
    case "REVOKED":
      return t("revoked");
  }
}

export function statusOrder(status: TicketPass["status"]): number {
  // VALID first, then USED/EXPIRED/REVOKED together.
  return status === "VALID" ? 0 : 1;
}
