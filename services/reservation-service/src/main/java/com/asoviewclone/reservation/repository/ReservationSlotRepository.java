package com.asoviewclone.reservation.repository;

import com.asoviewclone.reservation.model.ReservationSlot;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
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
    Timestamp ts = toSpannerTimestamp(now);

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

  public void deleteAll() {
    databaseClient.write(List.of(Mutation.delete("reservation_slots", com.google.cloud.spanner.KeySet.all())));
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

  private static Timestamp toSpannerTimestamp(Instant instant) {
    return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), 0);
  }
}
