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

  /**
   * @param providerPaymentId the gateway's intent id (e.g. Stripe {@code pi_...})
   * @param clientSecret the value the browser SDK needs to confirm the intent (Stripe Elements
   *     {@code pi_..._secret_...}). Stripe returns the real secret. Stub and Fake gateways return a
   *     deterministic synthetic value ({@code <intentId>_secret_(stub|fake)}) so local dev and
   *     Playwright tests can render the same UI flow without hitting Stripe. May be {@code null} if
   *     a future gateway has no equivalent concept.
   * @param redirectUrl provider-hosted URL the browser should navigate to in order to complete
   *     payment (PayPay QR / hosted checkout). {@code null} for gateways that confirm in-page via a
   *     client SDK (Stripe Elements uses {@code clientSecret} instead).
   * @param success whether the intent was successfully created on the provider
   */
  record PaymentResult(
      String providerPaymentId, String clientSecret, String redirectUrl, boolean success) {

    /** Backwards-compatible constructor for gateways without a hosted redirect URL. */
    public PaymentResult(String providerPaymentId, String clientSecret, boolean success) {
      this(providerPaymentId, clientSecret, null, success);
    }
  }
}
