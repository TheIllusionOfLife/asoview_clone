package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.Payment;
import java.util.Optional;

public interface PaymentService {

  /** Read-only lookup by provider id. Used by the webhook controller to resolve conflicts. */
  Optional<Payment> findByProviderPaymentId(String providerPaymentId);

  /**
   * Decide whether a webhook {@code ConflictException} is terminal (the provider should stop
   * retrying because nothing it sends will ever succeed) or transient (a short-term CAS loss that
   * is worth retrying). Terminal means: the payment is already {@code SUCCEEDED} or {@code FAILED},
   * or the associated order is already in a non-recoverable state ({@code PAID}, {@code CANCELLED},
   * {@code REFUNDED}).
   *
   * <p>Centralized here so the webhook controller does not need to know about order status.
   */
  boolean isTerminalConflict(String providerPaymentId);

  Payment createPaymentIntent(String orderId, String userId, String idempotencyKey);

  Payment confirmPayment(String paymentId);

  /**
   * Entry point used by the webhook controller. Looks up the payment by the provider-issued id,
   * then reuses {@link #confirmPayment} (plus a synchronous roll-forward from PENDING -&gt;
   * PAYMENT_PENDING if the webhook arrives before the AFTER_COMMIT listener has run).
   *
   * <p>Returns {@code null} when the provider id is unknown so the caller can respond 202 and let
   * the provider retry (the AFTER_COMMIT listener may not have seen the order yet).
   */
  Payment confirmByProviderPaymentId(String providerPaymentId);

  /** Marks a payment FAILED by provider id. Idempotent. Returns {@code null} when unknown. */
  Payment failByProviderPaymentId(String providerPaymentId);
}
