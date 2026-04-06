package com.asoviewclone.commercecore.orders.model;

import java.util.Set;

public enum OrderStatus {
  PENDING,
  PAYMENT_PENDING,
  CONFIRMING,
  PAID,
  CANCELLED,
  REFUNDED;

  private static final Set<OrderStatus> PENDING_TRANSITIONS = Set.of(PAYMENT_PENDING, CANCELLED);
  private static final Set<OrderStatus> PAYMENT_PENDING_TRANSITIONS =
      Set.of(CONFIRMING, CANCELLED);
  // CONFIRMING is an intermediate state held while the payment confirmation saga
  // is running. Cancel is intentionally not allowed: a concurrent cancel must wait
  // for CONFIRMING to resolve to PAID (success) or back to PAYMENT_PENDING (saga
  // failure) before it can transition.
  private static final Set<OrderStatus> CONFIRMING_TRANSITIONS = Set.of(PAID, PAYMENT_PENDING);
  private static final Set<OrderStatus> PAID_TRANSITIONS = Set.of(REFUNDED);

  public boolean canTransitionTo(OrderStatus target) {
    return switch (this) {
      case PENDING -> PENDING_TRANSITIONS.contains(target);
      case PAYMENT_PENDING -> PAYMENT_PENDING_TRANSITIONS.contains(target);
      case CONFIRMING -> CONFIRMING_TRANSITIONS.contains(target);
      case PAID -> PAID_TRANSITIONS.contains(target);
      case CANCELLED, REFUNDED -> false;
    };
  }
}
