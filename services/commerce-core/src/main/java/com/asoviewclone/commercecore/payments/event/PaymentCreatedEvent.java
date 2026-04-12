package com.asoviewclone.commercecore.payments.event;

/**
 * Application event published after a Payment row is persisted in a createPaymentIntent call. The
 * listener advances the corresponding Order to PAYMENT_PENDING after the enclosing transaction
 * commits, keeping the cross-store update off the critical path and recoverable via retry.
 */
public record PaymentCreatedEvent(
    String orderId, String paymentId, long amountJpy, String provider) {}
