package com.asoviewclone.commercecore.payments.controller;

import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/orders/{orderId}/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createPayment(
      @PathVariable String orderId,
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestBody Map<String, String> request) {
    String idempotencyKey = request.getOrDefault("idempotencyKey", orderId);
    Payment payment =
        paymentService.createPaymentIntent(orderId, user.userId().toString(), idempotencyKey);
    java.util.Map<String, Object> response = new java.util.HashMap<>();
    response.put("paymentId", payment.getPaymentId().toString());
    response.put("status", payment.getStatus().name());
    response.put("providerPaymentId", payment.getProviderPaymentId());
    // Stripe Elements on the frontend needs the client_secret to confirm the intent.
    // Persisted on the row by V8 so this is identical on idempotent replays.
    response.put("clientSecret", payment.getClientSecret());
    return response;
  }
}
