package com.asoviewclone.reservation.repository;

import com.asoviewclone.reservation.exception.ConflictException;
import com.asoviewclone.reservation.exception.NotFoundException;
import com.asoviewclone.reservation.model.ReservationSlot;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationSlotRepository {

  private final DatabaseClient databaseClient;

  public ReservationSlotRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  public ReservationSlot create(
      String tenantId,
      String venueId,
      String productId,
      String slotDate,
      String startTime,
      String endTime,
      long capacity) {
    String slotId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    databaseClient.write(
        List.of(
            Mutation.newInsertBuilder("reservation_slots")
                .set("slot_id")
                .to(slotId)
                .set("tenant_id")
                .to(tenantId)
                .set("venue_id")
                .to(venueId)
                .set("product_id")
                .to(productId)
                .set("slot_date")
                .to(slotDate)
                .set("start_time")
                .to(startTime)
                .set("end_time")
                .to(endTime)
                .set("capacity")
                .to(capacity)
                .set("approved_count")
                .to(0L)
                .set("waitlist_count")
                .to(0L)
                .set("created_at")
                .to(com.google.cloud.spanner.Value.COMMIT_TIMESTAMP)
                .set("updated_at")
                .to(com.google.cloud.spanner.Value.COMMIT_TIMESTAMP)
                .build()));

    return new ReservationSlot(
        slotId, tenantId, venueId, productId, slotDate, startTime, endTime, capacity, 0, 0, now,
        now);
  }

  public Optional<ReservationSlot> findById(String slotId) {
    Statement stmt =
        Statement.newBuilder("SELECT * FROM reservation_slots WHERE slot_id = @slotId")
            .bind("slotId")
            .to(slotId)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return Optional.of(fromResultSet(rs));
      }
    }
    return Optional.empty();
  }

  public List<ReservationSlot> findByVenueAndDate(String venueId, String date) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT * FROM reservation_slots"
                    + " WHERE venue_id = @venueId AND slot_date = @date"
                    + " ORDER BY start_time")
            .bind("venueId")
            .to(venueId)
            .bind("date")
            .to(date)
            .build();
    List<ReservationSlot> results = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        results.add(fromResultSet(rs));
      }
    }
    return results;
  }

  /**
   * Update slot time range and capacity. Blocked if any non-terminal reservation references this
   * slot (PENDING_APPROVAL, APPROVED, WAITLISTED). Verifies tenant ownership when tenantId is
   * non-null.
   */
  public ReservationSlot updateSlot(
      String slotId, String tenantId, String startTime, String endTime, long capacity) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              // Read current slot
              Statement slotStmt =
                  Statement.newBuilder("SELECT * FROM reservation_slots WHERE slot_id = @slotId")
                      .bind("slotId")
                      .to(slotId)
                      .build();
              ReservationSlot current;
              try (ResultSet rs = tx.executeQuery(slotStmt)) {
                if (!rs.next()) {
                  throw new NotFoundException("Slot not found: " + slotId);
                }
                current = fromResultSet(rs);
              }

              // Verify tenant ownership
              if (tenantId != null && !tenantId.equals(current.tenantId())) {
                throw new org.springframework.security.access.AccessDeniedException(
                    "Tenant mismatch on slot " + slotId);
              }

              // Guard: no non-terminal reservations
              guardNoActiveReservations(tx, slotId);

              tx.buffer(
                  Mutation.newUpdateBuilder("reservation_slots")
                      .set("slot_id")
                      .to(slotId)
                      .set("start_time")
                      .to(startTime)
                      .set("end_time")
                      .to(endTime)
                      .set("capacity")
                      .to(capacity)
                      .set("updated_at")
                      .to(Value.COMMIT_TIMESTAMP)
                      .build());

              return new ReservationSlot(
                  current.slotId(),
                  current.tenantId(),
                  current.venueId(),
                  current.productId(),
                  current.slotDate(),
                  startTime,
                  endTime,
                  capacity,
                  current.approvedCount(),
                  current.waitlistCount(),
                  current.createdAt(),
                  Instant.now());
            });
  }

  /**
   * Hard-delete a slot. Blocked if any non-terminal reservation references this slot. Verifies
   * tenant ownership when tenantId is non-null.
   */
  public void deleteSlot(String slotId, String tenantId) {
    databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              // Verify slot exists and check tenant
              Statement slotStmt =
                  Statement.newBuilder(
                          "SELECT slot_id, tenant_id FROM reservation_slots WHERE slot_id = @slotId")
                      .bind("slotId")
                      .to(slotId)
                      .build();
              try (ResultSet rs = tx.executeQuery(slotStmt)) {
                if (!rs.next()) {
                  throw new NotFoundException("Slot not found: " + slotId);
                }
                String slotTenantId = rs.getString("tenant_id");
                if (tenantId != null && !tenantId.equals(slotTenantId)) {
                  throw new org.springframework.security.access.AccessDeniedException(
                      "Tenant mismatch on slot " + slotId);
                }
              }

              // Guard: no non-terminal reservations
              guardNoActiveReservations(tx, slotId);

              tx.buffer(Mutation.delete("reservation_slots", Key.of(slotId)));
              return null;
            });
  }

  private void guardNoActiveReservations(
      com.google.cloud.spanner.TransactionContext tx, String slotId) {
    Statement guard =
        Statement.newBuilder(
                "SELECT COUNT(*) AS cnt FROM reservations"
                    + " WHERE slot_id = @slotId"
                    + " AND status IN ('PENDING_APPROVAL', 'APPROVED', 'WAITLISTED')")
            .bind("slotId")
            .to(slotId)
            .build();
    try (ResultSet rs = tx.executeQuery(guard)) {
      if (rs.next() && rs.getLong("cnt") > 0) {
        throw new ConflictException(
            "Cannot modify slot with active reservations (count=" + rs.getLong("cnt") + ")");
      }
    }
  }

  public com.asoviewclone.reservation.model.SlotUtilization getUtilization(
      String venueId, String tenantId) {
    String sql =
        "SELECT COUNT(*) AS total_slots,"
            + " COALESCE(SUM(capacity), 0) AS total_capacity,"
            + " COALESCE(SUM(approved_count), 0) AS total_approved"
            + " FROM reservation_slots WHERE venue_id = @venueId";
    if (tenantId != null) {
      sql += " AND tenant_id = @tenantId";
    }
    Statement.Builder builder = Statement.newBuilder(sql).bind("venueId").to(venueId);
    if (tenantId != null) {
      builder.bind("tenantId").to(tenantId);
    }
    try (ResultSet rs = databaseClient.singleUse().executeQuery(builder.build())) {
      if (rs.next()) {
        return new com.asoviewclone.reservation.model.SlotUtilization(
            rs.getLong("total_slots"), rs.getLong("total_capacity"), rs.getLong("total_approved"));
      }
    }
    return new com.asoviewclone.reservation.model.SlotUtilization(0, 0, 0);
  }

  public List<String> findDistinctVenueIds(String tenantId) {
    String sql = "SELECT DISTINCT venue_id FROM reservation_slots";
    if (tenantId != null) {
      sql += " WHERE tenant_id = @tenantId";
    }
    sql += " ORDER BY venue_id";
    Statement.Builder builder = Statement.newBuilder(sql);
    if (tenantId != null) {
      builder.bind("tenantId").to(tenantId);
    }
    List<String> venueIds = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(builder.build())) {
      while (rs.next()) {
        venueIds.add(rs.getString("venue_id"));
      }
    }
    return venueIds;
  }

  public void deleteAll() {
    databaseClient.write(
        List.of(Mutation.delete("reservation_slots", com.google.cloud.spanner.KeySet.all())));
  }

  private ReservationSlot fromResultSet(ResultSet rs) {
    return new ReservationSlot(
        rs.getString("slot_id"),
        rs.getString("tenant_id"),
        rs.getString("venue_id"),
        rs.getString("product_id"),
        rs.getString("slot_date"),
        rs.getString("start_time"),
        rs.getString("end_time"),
        rs.getLong("capacity"),
        rs.getLong("approved_count"),
        rs.getLong("waitlist_count"),
        rs.getTimestamp("created_at").toSqlTimestamp().toInstant(),
        rs.getTimestamp("updated_at").toSqlTimestamp().toInstant());
  }
}
