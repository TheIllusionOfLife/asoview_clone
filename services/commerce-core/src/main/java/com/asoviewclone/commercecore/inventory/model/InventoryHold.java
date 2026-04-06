package com.asoviewclone.commercecore.inventory.model;

import java.time.Instant;

public record InventoryHold(
    String holdId,
    String slotId,
    String userId,
    long quantity,
    Instant expiresAt,
    Instant createdAt) {

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }
}
