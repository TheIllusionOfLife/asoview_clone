package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Fallback gateway that always succeeds. Active by default; the Stripe implementation overrides
 * this via {@code @Primary @ConditionalOnProperty("payments.gateway=stripe")}.
 *
 * <p>Webhook verification is unsupported: the stub gateway has no signing key and is only wired in
 * local / unit-test profiles where webhook ingress is exercised through {@link FakePaymentGateway}
 * instead.
 */
@Component
@Primary
@ConditionalOnMissingBean(name = "stripePaymentGateway")
public class StubPaymentGateway implements PaymentGateway {

  public static final String PROVIDER_NAME = "STUB";

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PaymentResult createIntent(
      String orderId, BigDecimal amount, String currency, String idempotencyKey) {
    return new PaymentResult("stub-" + UUID.randomUUID(), true);
  }

  @Override
  public PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody) {
    throw new ValidationException(
        "StubPaymentGateway does not support webhook verification; enable 'stripe' profile or wire a FakePaymentGateway for tests");
  }
}
