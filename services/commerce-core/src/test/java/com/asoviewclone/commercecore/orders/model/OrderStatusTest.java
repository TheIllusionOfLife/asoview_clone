package com.asoviewclone.commercecore.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @Test
  void pendingCanTransitionToPaymentPending() {
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue();
  }

  @Test
  void pendingCanTransitionToCancelled() {
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
  }

  @Test
  void pendingCannotTransitionToPaid() {
    assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID)).isFalse();
  }

  @Test
  void paymentPendingCanTransitionToPaid() {
    assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PAID)).isTrue();
  }

  @Test
  void paidCanTransitionToRefunded() {
    assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
  }

  @Test
  void cancelledCannotTransitionToAnything() {
    for (OrderStatus s : OrderStatus.values()) {
      assertThat(OrderStatus.CANCELLED.canTransitionTo(s)).isFalse();
    }
  }
}
