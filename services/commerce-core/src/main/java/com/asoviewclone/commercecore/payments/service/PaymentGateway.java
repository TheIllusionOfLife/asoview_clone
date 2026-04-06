package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import java.math.BigDecimal;

public interface PaymentGateway {

  /** Name that is stored on {@code Payment.provider} when this gateway creates an intent. */
  String providerName();

  PaymentResult createIntent(String orderId, BigDecimal amount, String currency);

  /**
   * Verify and parse a raw webhook body from the provider. Throws {@link
   * com.asoviewclone.common.error.ValidationException} when the signature is missing/invalid.
   */
  PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody);

  record PaymentResult(String providerPaymentId, boolean success) {}
}
