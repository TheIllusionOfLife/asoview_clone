package com.asoviewclone.commercecore.orders.event;

/**
 * Published after a payment confirmation has advanced an order to PAID. The orchestrator wires the
 * publish call from PaymentServiceImpl.confirmPayment; listeners in other modules (points earn,
 * notifications, etc.) subscribe via @TransactionalEventListener(AFTER_COMMIT).
 */
public record OrderPaidEvent(String orderId, String userId, long subtotalJpy) {}
