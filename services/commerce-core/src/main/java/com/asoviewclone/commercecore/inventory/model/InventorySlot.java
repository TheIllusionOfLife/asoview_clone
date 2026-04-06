package com.asoviewclone.commercecore.inventory.model;

import java.time.Instant;

public record InventorySlot(
    String slotId,
    String productVariantId,
    String slotDate,
    String startTime,
    String endTime,
    long totalCapacity,
    long reservedCount,
    Instant createdAt) {

  public long availableCapacity(long activeHoldsQuantity) {
    return totalCapacity - reservedCount - activeHoldsQuantity;
  }
}
