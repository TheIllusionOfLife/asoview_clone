package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.PaymentGatewayEvent;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback gateway that always succeeds. Activated by default via {@code payments.gateway=stub} (or
 * by omitting the property entirely). Stripe and Fake (test) implementations use distinct
 * {@code @ConditionalOnProperty} values so only one of the three gateway beans is ever in the
 * context.
 *
 * <p>A previous version used {@code @ConditionalOnMissingBean(name = "stripePaymentGateway")} which
 * did not exclude the fake bean, causing a second {@code @Primary} bean to register under the
 * {@code payments.gateway=fake} test profile and triggering a {@code
 * NoUniqueBeanDefinitionException}.
 *
 * <p>Webhook verification is unsupported: the stub gateway has no signing key and is only wired in
 * local / unit-test profiles where webhook ingress is exercised through {@link FakePaymentGateway}
 * instead.
 */
@Component
@ConditionalOnProperty(name = "payments.gateway", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements PaymentGateway {

  public static final String PROVIDER_NAME = "STUB";

  @Override
  public String providerName() {
    return PROVIDER_NAME;
  }

  @Override
  public PaymentResult createIntent(
      String orderId, BigDecimal amount, String currency, String idempotencyKey) {
    String intentId = "stub-" + UUID.randomUUID();
    return new PaymentResult(intentId, intentId + "_secret_stub", true);
  }

  @Override
  public PaymentGatewayEvent verifyWebhook(String signatureHeader, byte[] rawBody) {
    throw new ValidationException(
        "StubPaymentGateway does not support webhook verification; enable 'stripe' profile or wire a FakePaymentGateway for tests");
  }
}
