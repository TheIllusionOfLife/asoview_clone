package com.asoviewclone.commercecore.inventory.repository;

import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.model.InventorySlot;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.time.ClockProvider;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TransactionContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InventorySlotRepository {

  private static final Duration HOLD_TTL = Duration.ofMinutes(15);

  private final DatabaseClient databaseClient;
  private final ClockProvider clockProvider;

  public InventorySlotRepository(DatabaseClient databaseClient, ClockProvider clockProvider) {
    this.databaseClient = databaseClient;
    this.clockProvider = clockProvider;
  }

  /**
   * Batched version of {@link #countActiveHoldQuantity(String)} for availability queries. Returns a
   * map keyed by slot id; slots with no active holds are present with value 0. One Spanner
   * single-use read for the entire set, replacing the former per-slot N+1.
   */
  public java.util.Map<String, Long> countActiveHoldQuantities(
      java.util.Collection<String> slotIds) {
    java.util.Map<String, Long> result = new java.util.HashMap<>();
    for (String slotId : slotIds) {
      result.put(slotId, 0L);
    }
    if (slotIds.isEmpty()) {
      return result;
    }
    Instant now = clockProvider.now();
    Statement stmt =
        Statement.newBuilder(
                "SELECT slot_id, SUM(quantity) AS total"
                    + " FROM inventory_holds"
                    + " WHERE slot_id IN UNNEST(@slotIds)"
                    + " AND expires_at > @now"
                    + " GROUP BY slot_id")
            .bind("slotIds")
            .toStringArray(slotIds)
            .bind("now")
            .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        result.put(rs.getString("slot_id"), rs.getLong("total"));
      }
    }
    return result;
  }

  /**
   * Read-side count of unexpired hold quantity for a slot. Uses a single-use read so it does not
   * require a transaction. Intended for availability queries; hot booking paths should continue to
   * use the transactional counterpart inside {@link #holdInventory}.
   */
  public long countActiveHoldQuantity(String slotId) {
    Instant now = clockProvider.now();
    Statement stmt =
        Statement.newBuilder(
                "SELECT COALESCE(SUM(quantity), 0) as total"
                    + " FROM inventory_holds"
                    + " WHERE slot_id = @slotId"
                    + " AND expires_at > @now")
            .bind("slotId")
            .to(slotId)
            .bind("now")
            .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getLong("total");
      }
    }
    return 0;
  }

  public List<InventorySlot> findAvailableSlots(
      String productVariantId, String startDate, String endDate) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT slot_id, product_variant_id, slot_date, start_time, end_time,"
                    + " total_capacity, reserved_count, created_at"
                    + " FROM inventory_slots"
                    + " WHERE product_variant_id = @variantId"
                    + " AND slot_date >= @startDate"
                    + " AND slot_date <= @endDate"
                    + " ORDER BY slot_date, start_time")
            .bind("variantId")
            .to(productVariantId)
            .bind("startDate")
            .to(startDate)
            .bind("endDate")
            .to(endDate)
            .build();

    List<InventorySlot> slots = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        slots.add(mapSlot(rs));
      }
    }
    return slots;
  }

  public InventoryHold holdInventory(String slotId, String userId, int quantity) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              InventorySlot slot = readSlotInTransaction(tx, slotId);
              long activeHolds = countActiveHoldsInTransaction(tx, slotId);
              long available = slot.availableCapacity(activeHolds);

              if (available < quantity) {
                throw new ConflictException(
                    "Insufficient capacity: requested " + quantity + ", available " + available);
              }

              String holdId = UUID.randomUUID().toString();
              Instant now = clockProvider.now();
              Instant expiresAt = now.plus(HOLD_TTL);

              String productVariantId = slot.productVariantId();
              tx.buffer(
                  Mutation.newInsertBuilder("inventory_holds")
                      .set("hold_id")
                      .to(holdId)
                      .set("slot_id")
                      .to(slotId)
                      .set("product_variant_id")
                      .to(productVariantId)
                      .set("user_id")
                      .to(userId)
                      .set("quantity")
                      .to(quantity)
                      .set("expires_at")
                      .to(Timestamp.ofTimeSecondsAndNanos(expiresAt.getEpochSecond(), 0))
                      .set("created_at")
                      .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
                      .build());

              return new InventoryHold(
                  holdId, slotId, productVariantId, userId, quantity, expiresAt, now);
            });
  }

  public void confirmHold(String holdId) {
    databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              // Idempotent: if hold is already gone (previously confirmed), no-op
              InventoryHold hold = readHoldInTransactionOrNull(tx, holdId);
              if (hold == null) {
                return null;
              }

              // Reject if hold has expired
              if (hold.isExpired(clockProvider.now())) {
                throw new ConflictException("Hold " + holdId + " has expired");
              }

              // Increment reserved count
              InventorySlot slot = readSlotInTransaction(tx, hold.slotId());
              tx.buffer(
                  Mutation.newUpdateBuilder("inventory_slots")
                      .set("slot_id")
                      .to(slot.slotId())
                      .set("product_variant_id")
                      .to(slot.productVariantId())
                      .set("slot_date")
                      .to(slot.slotDate())
                      .set("start_time")
                      .to(slot.startTime())
                      .set("end_time")
                      .to(slot.endTime())
                      .set("total_capacity")
                      .to(slot.totalCapacity())
                      .set("reserved_count")
                      .to(slot.reservedCount() + hold.quantity())
                      .set("created_at")
                      .to(Timestamp.ofTimeSecondsAndNanos(slot.createdAt().getEpochSecond(), 0))
                      .build());

              // Delete the hold
              tx.buffer(
                  Mutation.delete(
                      "inventory_holds",
                      com.google.cloud.spanner.KeySet.singleKey(
                          com.google.cloud.spanner.Key.of(holdId))));

              return null;
            });
  }

  /**
   * Decrements {@code reserved_count} on a slot whose hold has already been confirmed (i.e. the
   * hold row was deleted and the count incremented). Used by saga compensation to roll back a
   * previously-confirmed step. Throws {@link ConflictException} if {@code quantity} exceeds the
   * current {@code reserved_count}, surfacing duplicate or out-of-order compensation rather than
   * silently clamping to zero (which would erase an unrelated reservation).
   */
  public void releaseConfirmedHold(String slotId, long quantity) {
    databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              InventorySlot slot = readSlotInTransaction(tx, slotId);
              if (quantity > slot.reservedCount()) {
                throw new ConflictException(
                    "Cannot release "
                        + quantity
                        + " from slot "
                        + slotId
                        + " with reserved_count="
                        + slot.reservedCount());
              }
              long newReserved = slot.reservedCount() - quantity;
              tx.buffer(
                  Mutation.newUpdateBuilder("inventory_slots")
                      .set("slot_id")
                      .to(slot.slotId())
                      .set("product_variant_id")
                      .to(slot.productVariantId())
                      .set("slot_date")
                      .to(slot.slotDate())
                      .set("start_time")
                      .to(slot.startTime())
                      .set("end_time")
                      .to(slot.endTime())
                      .set("total_capacity")
                      .to(slot.totalCapacity())
                      .set("reserved_count")
                      .to(newReserved)
                      .set("created_at")
                      .to(Timestamp.ofTimeSecondsAndNanos(slot.createdAt().getEpochSecond(), 0))
                      .build());
              return null;
            });
  }

  public void releaseHold(String holdId) {
    databaseClient.write(
        List.of(
            Mutation.delete(
                "inventory_holds",
                com.google.cloud.spanner.KeySet.singleKey(
                    com.google.cloud.spanner.Key.of(holdId)))));
  }

  private InventorySlot readSlotInTransaction(TransactionContext tx, String slotId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT slot_id, product_variant_id, slot_date, start_time, end_time,"
                    + " total_capacity, reserved_count, created_at"
                    + " FROM inventory_slots WHERE slot_id = @slotId")
            .bind("slotId")
            .to(slotId)
            .build();
    try (ResultSet rs = tx.executeQuery(stmt)) {
      if (rs.next()) {
        return mapSlot(rs);
      }
    }
    throw new NotFoundException("InventorySlot", slotId);
  }

  private InventoryHold readHoldInTransactionOrNull(TransactionContext tx, String holdId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT hold_id, slot_id, product_variant_id, user_id, quantity,"
                    + " expires_at, created_at"
                    + " FROM inventory_holds WHERE hold_id = @holdId")
            .bind("holdId")
            .to(holdId)
            .build();
    try (ResultSet rs = tx.executeQuery(stmt)) {
      if (rs.next()) {
        return mapHold(rs);
      }
    }
    return null;
  }

  private InventoryHold readHoldInTransaction(TransactionContext tx, String holdId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT hold_id, slot_id, product_variant_id, user_id, quantity,"
                    + " expires_at, created_at"
                    + " FROM inventory_holds WHERE hold_id = @holdId")
            .bind("holdId")
            .to(holdId)
            .build();
    try (ResultSet rs = tx.executeQuery(stmt)) {
      if (rs.next()) {
        return mapHold(rs);
      }
    }
    throw new NotFoundException("InventoryHold", holdId);
  }

  private long countActiveHoldsInTransaction(TransactionContext tx, String slotId) {
    Instant now = clockProvider.now();
    Statement stmt =
        Statement.newBuilder(
                "SELECT COALESCE(SUM(quantity), 0) as total"
                    + " FROM inventory_holds"
                    + " WHERE slot_id = @slotId"
                    + " AND expires_at > @now")
            .bind("slotId")
            .to(slotId)
            .bind("now")
            .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
            .build();
    try (ResultSet rs = tx.executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getLong("total");
      }
    }
    return 0;
  }

  private InventorySlot mapSlot(ResultSet rs) {
    return new InventorySlot(
        rs.getString("slot_id"),
        rs.getString("product_variant_id"),
        rs.getString("slot_date"),
        rs.isNull("start_time") ? null : rs.getString("start_time"),
        rs.isNull("end_time") ? null : rs.getString("end_time"),
        rs.getLong("total_capacity"),
        rs.getLong("reserved_count"),
        rs.getTimestamp("created_at").toDate().toInstant());
  }

  private InventoryHold mapHold(ResultSet rs) {
    // product_variant_id was added by V5 as a nullable column, so holds created
    // before that migration land here with NULL. Spanner's getString() throws
    // IllegalStateException on NULL, mirroring the start_time/end_time handling
    // in mapSlot above.
    return new InventoryHold(
        rs.getString("hold_id"),
        rs.getString("slot_id"),
        rs.isNull("product_variant_id") ? null : rs.getString("product_variant_id"),
        rs.getString("user_id"),
        rs.getLong("quantity"),
        rs.getTimestamp("expires_at").toDate().toInstant(),
        rs.getTimestamp("created_at").toDate().toInstant());
  }
}
