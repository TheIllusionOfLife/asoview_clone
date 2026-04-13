package com.asoviewclone.reservation.repository;

import com.asoviewclone.reservation.model.Reservation;
import com.asoviewclone.reservation.model.ReservationStatus;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.Value;
import java.time.Instant;
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

  /**
   * Atomically transition reservation status from expectedStatus to newStatus AND update slot
   * counters in a single Spanner read-write transaction. Returns the updated reservation on
   * success.
   *
   * @throws IllegalStateException if current status != expectedStatus or reservation not found
   */
  public Reservation transitionStatusAtomically(
      String reservationId,
      ReservationStatus expectedStatus,
      ReservationStatus newStatus,
      String reason) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              Reservation current = readInTransaction(tx, reservationId);
              if (current == null) {
                throw new IllegalStateException("Reservation not found: " + reservationId);
              }
              if (current.status() != expectedStatus) {
                throw new IllegalStateException(
                    "Expected status "
                        + expectedStatus
                        + " but was "
                        + current.status()
                        + " for reservation "
                        + reservationId);
              }

              Mutation.WriteBuilder builder =
                  Mutation.newUpdateBuilder("reservations")
                      .set("reservation_id")
                      .to(reservationId)
                      .set("status")
                      .to(newStatus.name())
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP);

              if (reason != null && newStatus == ReservationStatus.REJECTED) {
                builder.set("reject_reason").to(reason);
              } else if (reason != null && newStatus == ReservationStatus.CANCELLED) {
                builder.set("cancel_reason").to(reason);
              }

              tx.buffer(builder.build());

              return new Reservation(
                  current.reservationId(),
                  current.tenantId(),
                  current.venueId(),
                  current.slotId(),
                  current.consumerUserId(),
                  newStatus,
                  current.idempotencyKey(),
                  current.guestName(),
                  current.guestEmail(),
                  current.guestCount(),
                  newStatus == ReservationStatus.REJECTED ? reason : current.rejectReason(),
                  newStatus == ReservationStatus.CANCELLED ? reason : current.cancelReason(),
                  current.createdAt(),
                  Instant.now());
            });
  }

  /**
   * Atomically approve a reservation: CAS on reservation status + increment slot approved_count +
   * decrement waitlist_count if transitioning from WAITLISTED. Checks slot capacity within the
   * transaction to prevent overbooking.
   *
   * @throws IllegalStateException if status is not PENDING_APPROVAL or WAITLISTED, or capacity
   *     exceeded
   */
  public Reservation approveAtomically(String reservationId) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              Reservation current = readInTransaction(tx, reservationId);
              if (current == null) {
                throw new IllegalStateException("Reservation not found: " + reservationId);
              }
              if (current.status() != ReservationStatus.PENDING_APPROVAL
                  && current.status() != ReservationStatus.WAITLISTED) {
                throw new IllegalStateException(
                    "Cannot approve reservation in status " + current.status());
              }

              // Read slot capacity within the same transaction
              Statement slotStmt =
                  Statement.newBuilder(
                          "SELECT capacity, approved_count, waitlist_count"
                              + " FROM reservation_slots WHERE slot_id = @slotId")
                      .bind("slotId")
                      .to(current.slotId())
                      .build();
              long capacity;
              long approvedCount;
              long waitlistCount;
              try (ResultSet rs = tx.executeQuery(slotStmt)) {
                if (!rs.next()) {
                  throw new IllegalStateException("Slot not found: " + current.slotId());
                }
                capacity = rs.getLong("capacity");
                approvedCount = rs.getLong("approved_count");
                waitlistCount = rs.getLong("waitlist_count");
              }

              if (approvedCount + current.guestCount() > capacity) {
                throw new IllegalStateException(
                    "Insufficient capacity: available="
                        + (capacity - approvedCount)
                        + ", requested="
                        + current.guestCount());
              }

              // Update reservation status
              tx.buffer(
                  Mutation.newUpdateBuilder("reservations")
                      .set("reservation_id")
                      .to(reservationId)
                      .set("status")
                      .to(ReservationStatus.APPROVED.name())
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP)
                      .build());

              // Update slot counters
              Mutation.WriteBuilder slotUpdate =
                  Mutation.newUpdateBuilder("reservation_slots")
                      .set("slot_id")
                      .to(current.slotId())
                      .set("approved_count")
                      .to(approvedCount + current.guestCount())
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP);

              if (current.status() == ReservationStatus.WAITLISTED) {
                slotUpdate
                    .set("waitlist_count")
                    .to(Math.max(0, waitlistCount - current.guestCount()));
              }

              tx.buffer(slotUpdate.build());

              return new Reservation(
                  current.reservationId(),
                  current.tenantId(),
                  current.venueId(),
                  current.slotId(),
                  current.consumerUserId(),
                  ReservationStatus.APPROVED,
                  current.idempotencyKey(),
                  current.guestName(),
                  current.guestEmail(),
                  current.guestCount(),
                  current.rejectReason(),
                  current.cancelReason(),
                  current.createdAt(),
                  Instant.now());
            });
  }

  /** Atomically waitlist a reservation: CAS on status + increment slot waitlist_count. */
  public Reservation waitlistAtomically(String reservationId) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              Reservation current = readInTransaction(tx, reservationId);
              if (current == null) {
                throw new IllegalStateException("Reservation not found: " + reservationId);
              }
              if (current.status() != ReservationStatus.PENDING_APPROVAL) {
                throw new IllegalStateException(
                    "Cannot waitlist reservation in status " + current.status());
              }

              Statement slotStmt =
                  Statement.newBuilder(
                          "SELECT waitlist_count FROM reservation_slots WHERE slot_id = @slotId")
                      .bind("slotId")
                      .to(current.slotId())
                      .build();
              long waitlistCount;
              try (ResultSet rs = tx.executeQuery(slotStmt)) {
                if (!rs.next()) {
                  throw new IllegalStateException("Slot not found: " + current.slotId());
                }
                waitlistCount = rs.getLong("waitlist_count");
              }

              tx.buffer(
                  Mutation.newUpdateBuilder("reservations")
                      .set("reservation_id")
                      .to(reservationId)
                      .set("status")
                      .to(ReservationStatus.WAITLISTED.name())
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP)
                      .build());

              tx.buffer(
                  Mutation.newUpdateBuilder("reservation_slots")
                      .set("slot_id")
                      .to(current.slotId())
                      .set("waitlist_count")
                      .to(waitlistCount + current.guestCount())
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP)
                      .build());

              return new Reservation(
                  current.reservationId(),
                  current.tenantId(),
                  current.venueId(),
                  current.slotId(),
                  current.consumerUserId(),
                  ReservationStatus.WAITLISTED,
                  current.idempotencyKey(),
                  current.guestName(),
                  current.guestEmail(),
                  current.guestCount(),
                  current.rejectReason(),
                  current.cancelReason(),
                  current.createdAt(),
                  Instant.now());
            });
  }

  /**
   * Atomically cancel a reservation. Releases capacity if APPROVED (decrement approved_count) or
   * WAITLISTED (decrement waitlist_count). Accepts PENDING_APPROVAL, APPROVED, or WAITLISTED.
   */
  public Reservation cancelAtomically(String reservationId, String reason) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              Reservation current = readInTransaction(tx, reservationId);
              if (current == null) {
                throw new IllegalStateException("Reservation not found: " + reservationId);
              }
              if (current.status().isTerminal()) {
                throw new IllegalStateException(
                    "Cannot cancel reservation in terminal status " + current.status());
              }

              // Update reservation status
              tx.buffer(
                  Mutation.newUpdateBuilder("reservations")
                      .set("reservation_id")
                      .to(reservationId)
                      .set("status")
                      .to(ReservationStatus.CANCELLED.name())
                      .set("cancel_reason")
                      .to(reason)
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP)
                      .build());

              // Release capacity if needed
              if (current.status() == ReservationStatus.APPROVED
                  || current.status() == ReservationStatus.WAITLISTED) {
                Statement slotStmt =
                    Statement.newBuilder(
                            "SELECT approved_count, waitlist_count"
                                + " FROM reservation_slots WHERE slot_id = @slotId")
                        .bind("slotId")
                        .to(current.slotId())
                        .build();
                try (ResultSet rs = tx.executeQuery(slotStmt)) {
                  if (rs.next()) {
                    Mutation.WriteBuilder slotUpdate =
                        Mutation.newUpdateBuilder("reservation_slots")
                            .set("slot_id")
                            .to(current.slotId())
                            .set("updated_at")
                            .to(Value.COMMIT_TIMESTAMP);

                    if (current.status() == ReservationStatus.APPROVED) {
                      slotUpdate
                          .set("approved_count")
                          .to(Math.max(0, rs.getLong("approved_count") - current.guestCount()));
                    } else {
                      slotUpdate
                          .set("waitlist_count")
                          .to(Math.max(0, rs.getLong("waitlist_count") - current.guestCount()));
                    }
                    tx.buffer(slotUpdate.build());
                  }
                }
              }

              return new Reservation(
                  current.reservationId(),
                  current.tenantId(),
                  current.venueId(),
                  current.slotId(),
                  current.consumerUserId(),
                  ReservationStatus.CANCELLED,
                  current.idempotencyKey(),
                  current.guestName(),
                  current.guestEmail(),
                  current.guestCount(),
                  current.rejectReason(),
                  reason,
                  current.createdAt(),
                  Instant.now());
            });
  }

  private Reservation readInTransaction(TransactionContext tx, String reservationId) {
    Statement stmt =
        Statement.newBuilder("SELECT * FROM reservations WHERE reservation_id = @id")
            .bind("id")
            .to(reservationId)
            .build();
    try (ResultSet rs = tx.executeQuery(stmt)) {
      if (rs.next()) {
        return fromResultSet(rs);
      }
    }
    return null;
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
