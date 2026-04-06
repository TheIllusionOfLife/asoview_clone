package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.payments.event.PaymentCreatedEvent;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentStatus;
import com.asoviewclone.commercecore.payments.repository.PaymentRepository;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final InventoryService inventoryService;
  private final PaymentGateway paymentGateway;
  private final EntitlementCreator entitlementCreator;
  private final ApplicationEventPublisher eventPublisher;

  public PaymentServiceImpl(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      InventoryService inventoryService,
      PaymentGateway paymentGateway,
      EntitlementCreator entitlementCreator,
      ApplicationEventPublisher eventPublisher) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.inventoryService = inventoryService;
    this.paymentGateway = paymentGateway;
    this.entitlementCreator = entitlementCreator;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public Payment createPaymentIntent(String orderId, String userId, String idempotencyKey) {
    // Idempotency check: verify the existing payment belongs to the same user and order
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      Payment ep = existing.get();
      if (!ep.getUserId().equals(userId)) {
        throw new ValidationException("Idempotency key already used by another user");
      }
      if (!ep.getOrderId().equals(orderId)) {
        throw new ValidationException("Idempotency key already used for a different order");
      }
      return ep;
    }

    Order order = orderRepository.findById(orderId);

    // Verify order ownership
    if (!order.userId().equals(userId)) {
      throw new ValidationException("Order does not belong to the authenticated user");
    }

    if (!order.status().canTransitionTo(OrderStatus.PAYMENT_PENDING)) {
      throw new ValidationException("Cannot create payment for order in status " + order.status());
    }

    Payment payment =
        new Payment(
            orderId, userId, new BigDecimal(order.totalAmount()), order.currency(), idempotencyKey);

    PaymentGateway.PaymentResult result =
        paymentGateway.createIntent(orderId, payment.getAmount(), payment.getCurrency());

    if (!result.success()) {
      throw new ConflictException("Payment gateway rejected the request");
    }

    payment.setProvider("STUB");
    payment.setProviderPaymentId(result.providerPaymentId());
    payment.setStatus(PaymentStatus.PROCESSING);

    // Save payment in the JPA transaction; the order status is advanced by an
    // AFTER_COMMIT transactional event listener with retry. This keeps the
    // cross-store write off the critical path and recoverable on transient failure.
    Payment saved = paymentRepository.save(payment);
    eventPublisher.publishEvent(new PaymentCreatedEvent(orderId));

    return saved;
  }

  @Override
  @Transactional
  public Payment confirmPayment(String paymentId) {
    Payment payment =
        paymentRepository
            .findById(java.util.UUID.fromString(paymentId))
            .orElseThrow(() -> new NotFoundException("Payment", paymentId));

    // Idempotent: already confirmed
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      return payment;
    }

    if (payment.getStatus() != PaymentStatus.PROCESSING) {
      throw new ConflictException("Cannot confirm payment in status " + payment.getStatus());
    }

    // Confirm inventory holds first. If this fails, payment stays PROCESSING
    // and can be retried by the caller.
    Order order = orderRepository.findById(payment.getOrderId());
    if (order.status() == OrderStatus.CANCELLED) {
      throw new ConflictException("Order has been cancelled, cannot confirm payment");
    }
    for (OrderItem item : order.items()) {
      if (item.holdId() != null) {
        inventoryService.confirmHold(item.holdId());
      }
    }

    // Create entitlements. If this fails, holds are confirmed but entitlements
    // are missing. The caller can retry (entitlement creation is idempotent).
    entitlementCreator.createEntitlementsForOrder(order);

    // Only mark SUCCEEDED after all downstream effects complete.
    payment.setStatus(PaymentStatus.SUCCEEDED);
    paymentRepository.save(payment);
    boolean swapped =
        orderRepository.updateStatusIf(
            payment.getOrderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAID);
    if (!swapped) {
      throw new ConflictException(
          "Order " + payment.getOrderId() + " status changed concurrently; confirm aborted");
    }

    return payment;
  }
}
