package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
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

  public PaymentServiceImpl(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      InventoryService inventoryService,
      PaymentGateway paymentGateway,
      EntitlementCreator entitlementCreator) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.inventoryService = inventoryService;
    this.paymentGateway = paymentGateway;
    this.entitlementCreator = entitlementCreator;
  }

  @Override
  @Transactional
  public Payment createPaymentIntent(String orderId, String userId, String idempotencyKey) {
    // Idempotency check
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return existing.get();
    }

    Order order = orderRepository.findById(orderId);
    if (!order.status().canTransitionTo(OrderStatus.PAYMENT_PENDING)) {
      throw new ValidationException("Cannot create payment for order in status " + order.status());
    }

    Payment payment =
        new Payment(
            orderId, userId, new BigDecimal(order.totalAmount()), order.currency(), idempotencyKey);

    PaymentGateway.PaymentResult result =
        paymentGateway.createIntent(orderId, payment.getAmount(), payment.getCurrency());
    payment.setProvider("STUB");
    payment.setProviderPaymentId(result.providerPaymentId());
    payment.setStatus(PaymentStatus.PROCESSING);

    orderRepository.updateStatus(orderId, OrderStatus.PAYMENT_PENDING);

    return paymentRepository.save(payment);
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

    payment.setStatus(PaymentStatus.SUCCEEDED);
    paymentRepository.save(payment);

    // Update order status
    orderRepository.updateStatus(payment.getOrderId(), OrderStatus.PAID);

    // Confirm inventory holds
    Order order = orderRepository.findById(payment.getOrderId());
    for (OrderItem item : order.items()) {
      // In production, we'd track hold IDs. For Phase 1, holds are confirmed
      // via the slot's reserved_count being incremented.
    }

    // Trigger entitlement creation
    try {
      entitlementCreator.createEntitlementsForOrder(order);
    } catch (Exception e) {
      log.error("Failed to create entitlements for order {}: {}", order.orderId(), e.getMessage());
    }

    return payment;
  }
}
