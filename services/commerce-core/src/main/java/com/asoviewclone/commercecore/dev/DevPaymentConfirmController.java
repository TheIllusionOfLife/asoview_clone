package com.asoviewclone.commercecore.dev;

import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.repository.PaymentRepository;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.asoviewclone.common.error.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dev-only shortcut to transition an order PENDING → PAID without driving Stripe. Enables the demo
 * video's {@code /me/points} shot (points are credited by the existing {@code PointEarnListener} on
 * {@code OrderPaidEvent}) and gives {@code /me/orders} at least one PAID row.
 *
 * <p>Gated behind {@code @Profile("dev")} AND {@code demo.seed.enabled=true}. Base kustomization
 * does not set the property; only the dev overlay enables it. The endpoint additionally checks that
 * the caller owns the order, so even with the property enabled, a signed-in attacker cannot promote
 * another user's order.
 *
 * <p>Implementation: reuse the production {@link PaymentService#createPaymentIntent} + {@link
 * PaymentService#confirmPayment} path. The stub payment gateway (active on dev via {@code
 * payments.gateway=stub} default) returns synthetic intent IDs; the saga, entitlement creation, and
 * {@code OrderPaidEvent} all fire exactly like they would for a real Stripe webhook.
 */
@RestController
@RequestMapping("/v1/dev")
@Profile("dev")
@ConditionalOnProperty(name = "demo.seed.enabled", havingValue = "true")
public class DevPaymentConfirmController {

  private static final Logger log = LoggerFactory.getLogger(DevPaymentConfirmController.class);

  private final PaymentService paymentService;
  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;

  public DevPaymentConfirmController(
      PaymentService paymentService,
      PaymentRepository paymentRepository,
      OrderRepository orderRepository) {
    this.paymentService = paymentService;
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
  }

  @PostMapping("/orders/{orderId}/mark-paid")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> markPaid(
      @PathVariable String orderId, @AuthenticationPrincipal AuthenticatedUser user) {
    if (user == null || user.userId() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
    }

    Order order;
    try {
      order = orderRepository.findById(orderId);
    } catch (NotFoundException e) {
      // Non-enumeration: same 404 for "missing" and "not yours".
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
    }
    if (order == null || !order.userId().equals(user.userId().toString())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
    }

    if (order.status() == OrderStatus.PAID) {
      return response(orderId, "PAID", "already_paid");
    }

    // Idempotent: findByOrderId returns the existing active payment (if any).
    // Otherwise create one via the stub gateway; the AFTER_COMMIT listener
    // transitions the order PENDING → PAYMENT_PENDING synchronously after the
    // createPaymentIntent call returns, because @TransactionalEventListener
    // runs in the committer's thread after commit.
    Payment payment =
        paymentRepository
            .findByOrderId(orderId)
            .orElseGet(
                () ->
                    paymentService.createPaymentIntent(
                        orderId, user.userId().toString(), "demo-mark-paid:" + orderId));

    log.info(
        "DevPaymentConfirmController: confirming payment {} for order {} (user {})",
        payment.getPaymentId(),
        orderId,
        user.userId());

    Payment confirmed = paymentService.confirmPayment(payment.getPaymentId().toString());
    return response(orderId, "PAID", confirmed.getStatus().name());
  }

  private static Map<String, Object> response(String orderId, String orderStatus, String detail) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("orderId", orderId);
    body.put("orderStatus", orderStatus);
    body.put("detail", detail);
    return body;
  }
}
