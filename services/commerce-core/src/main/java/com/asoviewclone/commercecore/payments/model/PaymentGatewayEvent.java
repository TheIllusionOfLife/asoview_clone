package com.asoviewclone.commercecore.payments.model;

/**
 * Normalized webhook event produced by a {@link
 * com.asoviewclone.commercecore.payments.service.PaymentGateway} implementation after it has
 * verified a provider-specific signature on an incoming webhook body. Decouples the webhook
 * controller from provider SDK types so that Stripe, PayPay, and test fakes can all feed the same
 * downstream handler.
 *
 * <p>{@code rawEventId} is the provider's own event identifier (e.g., Stripe {@code evt_...}) and
 * is recorded in {@code processed_webhook_events} to block replays.
 */
public record PaymentGatewayEvent(
    String rawEventId, String providerPaymentId, Status status, String provider) {

  public enum Status {
    SUCCEEDED,
    FAILED,
    /** Provider fired an event we do not act on (e.g., {@code payment_intent.created}). */
    IGNORED
  }
}
