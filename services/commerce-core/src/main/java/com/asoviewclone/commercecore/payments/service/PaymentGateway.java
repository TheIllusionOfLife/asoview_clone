package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import java.math.BigDecimal;

public interface PaymentGateway {

  /** Name that is stored on {@code Payment.provider} when this gateway creates an intent. */
  String providerName();

  /**
   * Create a provider-side payment intent. {@code idempotencyKey} MUST be forwarded to the
   * provider's own idempotency header (Stripe: {@code Idempotency-Key}) so that a retry of this
   * method after a local JPA save failure does not mint a duplicate intent on the provider.
   */
  PaymentResult createIntent(
      String orderId, BigDecimal amount, String currency, String idempotencyKey);

  /**
   * Verify and parse a raw webhook body from the provider. Throws {@link
   * com.asoviewclone.common.error.ValidationException} when the signature is missing/invalid.
   */
  PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody);

  record PaymentResult(String providerPaymentId, boolean success) {}
}
