"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";

type Props = {
  action: string;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
};

export function ReasonModal({ action, onConfirm, onCancel }: Props) {
  const t = useTranslations("reservations");
  const [reason, setReason] = useState("");

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="bg-white rounded-[var(--radius-lg)] shadow-xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold mb-4">{action}</h3>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t("reasonPlaceholder")}
          rows={3}
          className="w-full px-3 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] mb-4 resize-none"
        />
        <div className="flex gap-3 justify-end">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm"
          >
            {t("cancel")}
          </button>
          <button
            type="button"
            onClick={() => onConfirm(reason.trim())}
            disabled={!reason.trim()}
            className="px-4 py-2 bg-[var(--color-danger)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
          >
            {t("confirm")}
          </button>
        </div>
      </div>
    </div>
  );
}
