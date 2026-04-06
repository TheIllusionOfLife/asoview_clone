package com.asoviewclone.commercecore.payments.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

// Fallback gateway that always succeeds. Will be replaced by a real
// implementation (e.g., Stripe via @Primary) in Phase 2.
@Component
public class StubPaymentGateway implements PaymentGateway {

  @Override
  public PaymentResult createIntent(String orderId, BigDecimal amount, String currency) {
    return new PaymentResult("stub-" + UUID.randomUUID(), true);
  }
}
