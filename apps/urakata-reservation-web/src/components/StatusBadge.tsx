"use client";

import { useTranslations } from "next-intl";

const statusColors: Record<string, string> = {
  PENDING_APPROVAL: "bg-yellow-100 text-yellow-800",
  APPROVED: "bg-green-100 text-green-800",
  WAITLISTED: "bg-blue-100 text-blue-800",
  REJECTED: "bg-red-100 text-red-800",
  CANCELLED: "bg-gray-100 text-gray-800",
  COMPLETED: "bg-purple-100 text-purple-800",
};

export function StatusBadge({ status }: { status: string }) {
  const t = useTranslations("status");
  const colors = statusColors[status] ?? "bg-gray-100 text-gray-800";
  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded-full ${colors}`}>
      {t(status as "PENDING_APPROVAL" | "APPROVED" | "WAITLISTED" | "REJECTED" | "CANCELLED" | "COMPLETED")}
    </span>
  );
}
