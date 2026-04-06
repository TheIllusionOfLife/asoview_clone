package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Test-only gateway that lets integration tests synthesize webhook callbacks deterministically
 * without reaching out to Stripe. Activated with {@code payments.gateway=fake}. Signature
 * verification is a shared-secret string comparison; payloads are plain JSON lines of the form
 * {@code <eventId>:<providerPaymentId>:<SUCCEEDED|FAILED>}.
 *
 * <p>Kept under {@code src/test} so the production jar never ships it.
 */
@Component("fakePaymentGateway")
@Primary
@ConditionalOnProperty(name = "payments.gateway", havingValue = "fake")
public class FakePaymentGateway implements PaymentGateway {

  public static final String PROVIDER_NAME = "FAKE";
  public static final String SECRET = "fake-webhook-secret";

  private final ConcurrentMap<String, String> intentToOrder = new ConcurrentHashMap<>();

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PaymentResult createIntent(
      String orderId, BigDecimal amount, String currency, String idempotencyKey) {
    String intentId = "fake-" + UUID.randomUUID();
    intentToOrder.put(intentId, orderId);
    return new PaymentResult(intentId, true);
  }

  @Override
  public PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody) {
    if (!SECRET.equals(signatureHeader)) {
      throw new ValidationException("Fake webhook signature mismatch");
    }
    String body = new String(rawBody);
    String[] parts = body.split(":", 3);
    if (parts.length != 3) {
      throw new ValidationException(
          "Fake webhook payload must be <eventId>:<providerPaymentId>:<SUCCEEDED|FAILED|IGNORED>");
    }
    PaymentGatewayEvent.Status status;
    try {
      status = PaymentGatewayEvent.Status.valueOf(parts[2]);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown fake webhook status: " + parts[2]);
    }
    return new PaymentGatewayEvent(parts[0], parts[1], status, PROVIDER_NAME);
  }
}
