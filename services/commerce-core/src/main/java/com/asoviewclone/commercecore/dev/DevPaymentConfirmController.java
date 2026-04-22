package com.asoviewclone.commercecore.dev;

import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentStatus;
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
      // OrderRepository.findById throws NotFoundException on missing rows
      // (never returns null), so there is no separate null-check branch.
      order = orderRepository.findById(orderId);
    } catch (NotFoundException e) {
      // Non-enumeration: same 404 for "missing" and "not yours".
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
    }
    if (!order.userId().equals(user.userId().toString())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found");
    }

    if (order.status() == OrderStatus.PAID) {
      return response(orderId, "PAID", "already_paid");
    }

    // Find the latest non-failed payment for the order, if any. A plain
    // findByOrderId() would throw IncorrectResultSizeDataAccessException
    // when FAILED rows accumulate alongside an active row — a realistic
    // state after a retry.
    //
    // If nothing usable exists, call createPaymentIntent. That call races
    // the DB's partial-unique-index on (order_id, status IN ('CREATED',
    // 'PROCESSING')); the loser translates to a ConflictException. We catch
    // that and re-query for the in-flight row — if our first query missed a
    // row due to a serialization timing issue, the second one will see it.
    java.util.List<Payment> allForOrder =
        paymentRepository.findAllForOrderOrderByCreatedAtDesc(orderId);
    log.info(
        "DevPaymentConfirmController: order {} has {} payment row(s): {}",
        orderId,
        allForOrder.size(),
        allForOrder.stream().map(p -> p.getPaymentId() + "=" + p.getStatus()).toList());
    Payment payment =
        allForOrder.stream()
            .filter(p -> p.getStatus() != PaymentStatus.FAILED)
            .findFirst()
            .orElse(null);
    if (payment == null) {
      try {
        payment =
            paymentService.createPaymentIntent(
                orderId, user.userId().toString(), "demo-mark-paid:" + orderId);
      } catch (com.asoviewclone.common.error.ConflictException e) {
        // A concurrent/stale in-flight payment exists. Re-query and use it.
        payment =
            paymentRepository.findAllForOrderOrderByCreatedAtDesc(orderId).stream()
                .filter(p -> p.getStatus() != PaymentStatus.FAILED)
                .findFirst()
                .orElseThrow(() -> e);
      }
    }

    // The AFTER_COMMIT listener that moves PENDING -> PAYMENT_PENDING is
    // @Retryable; after retries exhaust, the listener exception is swallowed
    // and the order stays PENDING. confirmPayment requires PAYMENT_PENDING,
    // so a defensive CAS here ensures we never immediately race the listener.
    // Returns false if the order is already past PENDING (listener fired) —
    // that is the happy path and not an error.
    orderRepository.updateStatusIf(orderId, OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING);

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
