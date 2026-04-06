package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.payments.model.Payment;

public interface PaymentService {

  Payment createPaymentIntent(String orderId, String userId, String idempotencyKey);

  Payment confirmPayment(String paymentId);
}
