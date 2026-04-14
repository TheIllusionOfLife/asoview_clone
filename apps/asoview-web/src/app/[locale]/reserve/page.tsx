import { ReserveClient } from "./ReserveClient";

export const dynamic = "force-dynamic";

export default function ReservePage() {
  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <ReserveClient />
    </div>
  );
}
