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
 * Test-only PayPay gateway mirror of {@link FakePaymentGateway}. Activated with {@code
 * payments.gateway=fake-paypay}. Signature verification is a shared-secret equality check; the raw
 * body is expected to be {@code <eventId>:<merchantPaymentId>:<state>} where state is one of {@code
 * SUCCEEDED|FAILED|IGNORED}.
 */
@Component("fakePayPayGateway")
@Primary
@ConditionalOnProperty(name = "payments.gateway", havingValue = "fake-paypay")
public class FakePayPayGateway implements PaymentGateway {

  public static final String PROVIDER_NAME = "PAYPAY";
  public static final String SECRET = "fake-paypay-webhook-secret";

  private final ConcurrentMap<String, String> intentToOrder = new ConcurrentHashMap<>();

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PaymentResult createIntent(
      String orderId, BigDecimal amount, String currency, String idempotencyKey) {
    String merchantPaymentId = "mp-" + UUID.randomUUID();
    intentToOrder.put(merchantPaymentId, orderId);
    return new PaymentResult(merchantPaymentId, merchantPaymentId, true);
  }

  @Override
  public PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody) {
    if (!SECRET.equals(signatureHeader)) {
      throw new ValidationException("Fake PayPay webhook signature mismatch");
    }
    String body = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
    String[] parts = body.split(":", 3);
    if (parts.length != 3) {
      throw new ValidationException(
          "Fake PayPay webhook payload must be <eventId>:<merchantPaymentId>:<SUCCEEDED|FAILED|IGNORED>");
    }
    PaymentGatewayEvent.Status status;
    try {
      status = PaymentGatewayEvent.Status.valueOf(parts[2]);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Unknown fake PayPay status: " + parts[2]);
    }
    return new PaymentGatewayEvent(parts[0], parts[1], status, PROVIDER_NAME);
  }
}
