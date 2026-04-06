package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.ValidationException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Real Stripe implementation. Activated with {@code payments.gateway=stripe}. Requires {@code
 * STRIPE_SECRET_KEY} and {@code STRIPE_WEBHOOK_SECRET} env vars (or equivalent properties).
 *
 * <p>Only JPY zero-decimal handling is supported today — the amount we receive is already in whole
 * yen so no multiplication is needed. If we add USD/EUR later, we will need to move this into a
 * currency table.
 */
@Component("stripePaymentGateway")
@Primary
@ConditionalOnProperty(name = "payments.gateway", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {

  public static final String PROVIDER_NAME = "STRIPE";

  private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

  @Value("${payments.stripe.secret-key:}")
  private String secretKey;

  @Value("${payments.stripe.webhook-secret:}")
  private String webhookSecret;

  @PostConstruct
  void init() {
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalStateException(
          "payments.stripe.secret-key must be set when payments.gateway=stripe");
    }
    if (webhookSecret == null || webhookSecret.isBlank()) {
      throw new IllegalStateException(
          "payments.stripe.webhook-secret must be set when payments.gateway=stripe");
    }
    Stripe.apiKey = secretKey;
  }

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PaymentResult createIntent(String orderId, BigDecimal amount, String currency) {
    try {
      PaymentIntentCreateParams params =
          PaymentIntentCreateParams.builder()
              .setAmount(toMinorUnits(amount, currency))
              .setCurrency(currency.toLowerCase())
              .putMetadata("order_id", orderId)
              .setAutomaticPaymentMethods(
                  PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                      .setEnabled(true)
                      .build())
              .build();
      PaymentIntent intent = PaymentIntent.create(params);
      return new PaymentResult(intent.getId(), true);
    } catch (StripeException e) {
      log.warn("Stripe createIntent failed for order {}: {}", orderId, e.getMessage());
      throw new ConflictException("Stripe rejected payment intent creation: " + e.getMessage());
    }
  }

  @Override
  public PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody) {
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new ValidationException("Missing Stripe-Signature header");
    }
    Event event;
    try {
      event = Webhook.constructEvent(new String(rawBody), signatureHeader, webhookSecret);
    } catch (SignatureVerificationException e) {
      throw new ValidationException("Stripe webhook signature verification failed");
    }

    String type = event.getType();
    PaymentGatewayEvent.Status mapped;
    if ("payment_intent.succeeded".equals(type)) {
      mapped = PaymentGatewayEvent.Status.SUCCEEDED;
    } else if ("payment_intent.payment_failed".equals(type)
        || "payment_intent.canceled".equals(type)) {
      mapped = PaymentGatewayEvent.Status.FAILED;
    } else {
      return new PaymentGatewayEvent(
          event.getId(), null, PaymentGatewayEvent.Status.IGNORED, PROVIDER_NAME);
    }

    EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
    Optional<com.stripe.model.StripeObject> obj = deserializer.getObject();
    String providerPaymentId =
        obj.filter(PaymentIntent.class::isInstance)
            .map(PaymentIntent.class::cast)
            .map(PaymentIntent::getId)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "Stripe webhook missing payment_intent payload for event "
                            + event.getId()));

    return new PaymentGatewayEvent(event.getId(), providerPaymentId, mapped, PROVIDER_NAME);
  }

  /**
   * Convert a decimal-formatted domain amount to Stripe minor units. JPY and other zero-decimal
   * currencies use the amount as-is; everything else multiplies by 100.
   */
  private static long toMinorUnits(BigDecimal amount, String currency) {
    if (isZeroDecimal(currency)) {
      return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    return amount
        .multiply(BigDecimal.valueOf(100))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact();
  }

  private static boolean isZeroDecimal(String currency) {
    // Stripe's published list of zero-decimal currencies. Keep minimal for phase 2.
    return Map.of(
            "JPY", true,
            "KRW", true,
            "VND", true)
        .getOrDefault(currency.toUpperCase(), false);
  }
}
