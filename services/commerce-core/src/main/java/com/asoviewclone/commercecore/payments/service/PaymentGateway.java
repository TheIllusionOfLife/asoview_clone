package com.asoviewclone.commercecore.payments.service;

import java.math.BigDecimal;

public interface PaymentGateway {

  PaymentResult createIntent(String orderId, BigDecimal amount, String currency);

  record PaymentResult(String providerPaymentId, boolean success) {}
}
