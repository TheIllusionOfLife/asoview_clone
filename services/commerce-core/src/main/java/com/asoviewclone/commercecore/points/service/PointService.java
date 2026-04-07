package com.asoviewclone.commercecore.points.service;

import java.util.UUID;

public interface PointService {

  long getBalance(UUID userId);

  /** Credit points for a paid order. Idempotent per (EARN_PURCHASE, orderId). */
  void earn(UUID userId, long amount, String orderId);

  /** Debit points used as a discount on an order. Idempotent per (BURN_PURCHASE, orderId). */
  void burn(UUID userId, long amount, String orderId);

  /**
   * Re-credit burned points when an order is cancelled. Idempotent per (REFUND_CANCEL, orderId).
   */
  void refund(UUID userId, long amount, String orderId);
}
