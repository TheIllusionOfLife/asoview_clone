package com.asoviewclone.commercecore.points.controller.dto;

import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import java.time.Instant;
import java.util.UUID;

public record PointLedgerResponse(
    UUID id, String direction, long amount, String reason, String referenceId, Instant createdAt) {

  public static PointLedgerResponse from(PointLedgerEntry entry) {
    String direction =
        switch (entry.getReason()) {
          case EARN_PURCHASE -> "EARN";
          case BURN_PURCHASE -> "BURN";
          case REFUND_CANCEL -> "REFUND";
        };
    long amount = Math.abs(entry.getDelta());
    return new PointLedgerResponse(
        entry.getId(),
        direction,
        amount,
        entry.getReason().name(),
        entry.getOrderId(),
        entry.getCreatedAt());
  }
}
