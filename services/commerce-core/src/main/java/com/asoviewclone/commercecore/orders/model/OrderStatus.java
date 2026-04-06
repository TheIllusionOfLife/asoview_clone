package com.asoviewclone.commercecore.orders.model;

import java.util.Set;

public enum OrderStatus {
  PENDING,
  PAYMENT_PENDING,
  PAID,
  CANCELLED,
  REFUNDED;

  private static final Set<OrderStatus> PENDING_TRANSITIONS = Set.of(PAYMENT_PENDING, CANCELLED);
  private static final Set<OrderStatus> PAYMENT_PENDING_TRANSITIONS = Set.of(PAID, CANCELLED);
  private static final Set<OrderStatus> PAID_TRANSITIONS = Set.of(REFUNDED);

  public boolean canTransitionTo(OrderStatus target) {
    return switch (this) {
      case PENDING -> PENDING_TRANSITIONS.contains(target);
      case PAYMENT_PENDING -> PAYMENT_PENDING_TRANSITIONS.contains(target);
      case PAID -> PAID_TRANSITIONS.contains(target);
      case CANCELLED, REFUNDED -> false;
    };
  }
}
