package com.asoviewclone.reservation.model;

import java.time.Instant;

public record ReservationSlot(
    String slotId,
    String tenantId,
    String venueId,
    String productId,
    String slotDate,
    String startTime,
    String endTime,
    long capacity,
    long approvedCount,
    long waitlistCount,
    Instant createdAt,
    Instant updatedAt) {

  public long availableCapacity() {
    return capacity - approvedCount;
  }
}
