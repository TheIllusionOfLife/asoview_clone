import { ReservationDetailClient } from "./ReservationDetailClient";

export const dynamic = "force-dynamic";

export default async function ReservationDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <div className="mx-auto max-w-2xl px-4 py-10">
      <ReservationDetailClient reservationId={id} />
    </div>
  );
}
