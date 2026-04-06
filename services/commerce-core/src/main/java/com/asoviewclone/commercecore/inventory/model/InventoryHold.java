package com.asoviewclone.commercecore.inventory.model;

import java.time.Instant;

public record InventoryHold(
    String holdId,
    String slotId,
    String productVariantId,
    String userId,
    long quantity,
    Instant expiresAt,
    Instant createdAt) {

  /**
   * Expired iff {@code expiresAt <= now}. The active-hold read query in {@code
   * InventorySlotRepository} uses {@code expires_at > @now} (active iff strictly after now), so the
   * boundary at {@code expiresAt == now} must be classified as expired here too — otherwise a hold
   * could be considered confirmable while the read path already excludes it from active counts,
   * allowing a double-book at the exact tick.
   */
  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }
}
