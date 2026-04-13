package com.asoviewclone.reservation.repository;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepository {

  private final DatabaseClient databaseClient;

  public ReservationRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public Reservation create(
      String tenantId,
      String venueId,
      String slotId,
      String consumerUserId,
      String idempotencyKey,
      String guestName,
      String guestEmail,
      int guestCount) {
    String reservationId = UUID.randomUUID().toString();

    databaseClient.write(
        List.of(
            Mutation.newInsertBuilder("reservations")
                .set("reservation_id")
                .to(reservationId)
                .set("tenant_id")
                .to(tenantId)
                .set("venue_id")
                .to(venueId)
                .set("slot_id")
                .to(slotId)
                .set("consumer_user_id")
                .to(consumerUserId)
                .set("status")
                .to(ReservationStatus.PENDING_APPROVAL.name())
                .set("idempotency_key")
                .to(idempotencyKey)
                .set("guest_name")
                .to(guestName)
                .set("guest_email")
                .to(guestEmail)
                .set("guest_count")
                .to(guestCount)
                .set("created_at")
                .to(Value.COMMIT_TIMESTAMP)
                .set("updated_at")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));

    return new Reservation(
        reservationId,
        tenantId,
        venueId,
        slotId,
        consumerUserId,
        ReservationStatus.PENDING_APPROVAL,
        idempotencyKey,
        guestName,
        guestEmail,
        guestCount,
        null,
        null,
        null,
        null);
  }

  public Optional<Reservation> findById(String reservationId) {
    Statement stmt =
        Statement.newBuilder("SELECT * FROM reservations WHERE reservation_id = @id")
            .bind("id")
            .to(reservationId)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return Optional.of(fromResultSet(rs));
      }
    }
    return Optional.empty();
  }

  public Optional<Reservation> findByIdempotencyKey(String idempotencyKey) {
    Statement stmt =
        Statement.newBuilder("SELECT * FROM reservations WHERE idempotency_key = @key")
            .bind("key")
            .to(idempotencyKey)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return Optional.of(fromResultSet(rs));
      }
    }
    return Optional.empty();
  }

  public List<Reservation> findByConsumerUserId(String consumerUserId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT * FROM reservations WHERE consumer_user_id = @userId"
                    + " ORDER BY created_at DESC")
            .bind("userId")
            .to(consumerUserId)
            .build();
    return executeQuery(stmt);
  }

  public List<Reservation> findByVenueAndStatus(String venueId, ReservationStatus status) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT * FROM reservations WHERE venue_id = @venueId AND status = @status"
                    + " ORDER BY created_at DESC")
            .bind("venueId")
            .to(venueId)
            .bind("status")
            .to(status.name())
            .build();
    return executeQuery(stmt);
  }

  public void deleteAll() {
    databaseClient.write(List.of(Mutation.delete("reservations", KeySet.all())));
  }

  private List<Reservation> executeQuery(Statement stmt) {
    List<Reservation> results = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        results.add(fromResultSet(rs));
      }
    }
    return results;
  }

  private Reservation fromResultSet(ResultSet rs) {
    return new Reservation(
        rs.getString("reservation_id"),
        rs.getString("tenant_id"),
        rs.getString("venue_id"),
        rs.getString("slot_id"),
        rs.getString("consumer_user_id"),
        ReservationStatus.valueOf(rs.getString("status")),
        rs.getString("idempotency_key"),
        rs.getString("guest_name"),
        rs.getString("guest_email"),
        (int) rs.getLong("guest_count"),
        rs.isNull("reject_reason") ? null : rs.getString("reject_reason"),
        rs.isNull("cancel_reason") ? null : rs.getString("cancel_reason"),
        rs.getTimestamp("created_at").toSqlTimestamp().toInstant(),
        rs.getTimestamp("updated_at").toSqlTimestamp().toInstant());
  }
}
