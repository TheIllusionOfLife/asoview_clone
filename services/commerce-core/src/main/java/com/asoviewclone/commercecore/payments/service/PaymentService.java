package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.Payment;

public interface PaymentService {

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
