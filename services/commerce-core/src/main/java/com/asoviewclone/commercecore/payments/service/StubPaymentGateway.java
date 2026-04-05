package com.asoviewclone.commercecore.payments.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class StubPaymentGateway implements PaymentGateway {

  @Override
  public PaymentResult createIntent(String orderId, BigDecimal amount, String currency) {
    return new PaymentResult("stub-" + UUID.randomUUID(), true);
  }
}
