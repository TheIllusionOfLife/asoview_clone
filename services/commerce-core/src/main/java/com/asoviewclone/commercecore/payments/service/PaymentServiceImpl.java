package com.asoviewclone.commercecore.payments.service;

import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.payments.event.PaymentCreatedEvent;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentStatus;
import com.asoviewclone.commercecore.payments.repository.PaymentRepository;
import com.asoviewclone.commercecore.payments.saga.PaymentConfirmationSaga;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final InventoryService inventoryService;
  private final PaymentGateway paymentGateway;
  private final EntitlementCreator entitlementCreator;
  private final ApplicationEventPublisher eventPublisher;
  private final PaymentConfirmationSaga paymentConfirmationSaga;
  private final TransactionTemplate requiresNewTxTemplate;

  public PaymentServiceImpl(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      InventoryService inventoryService,
      PaymentGateway paymentGateway,
      EntitlementCreator entitlementCreator,
      ApplicationEventPublisher eventPublisher,
      PaymentConfirmationSaga paymentConfirmationSaga,
      PlatformTransactionManager transactionManager) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.inventoryService = inventoryService;
    this.paymentGateway = paymentGateway;
    this.entitlementCreator = entitlementCreator;
    this.eventPublisher = eventPublisher;
    this.paymentConfirmationSaga = paymentConfirmationSaga;
    this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTxTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    payment.setProvider(paymentGateway.providerName());
    payment.setProviderPaymentId(result.providerPaymentId());
    payment.setStatus(PaymentStatus.PROCESSING);

    // Save payment in the JPA transaction; the order status is advanced by an
    // AFTER_COMMIT transactional event listener with retry. This keeps the
    // cross-store write off the critical path and recoverable on transient failure.
    //
    // A partial unique index on payments(order_id) WHERE status IN ('CREATED','PROCESSING')
    // enforces single in-flight payment per order. Concurrent createPaymentIntent
    // calls with different idempotency keys will race here: the loser gets a
    // DataIntegrityViolationException that we translate to a ConflictException.
    Payment saved;
    try {
      saved = paymentRepository.save(payment);
      paymentRepository.flush();
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      throw new ConflictException("Order " + orderId + " already has a payment in flight");
    }
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

    Order order = orderRepository.findById(payment.getOrderId());
    if (order.status() == OrderStatus.CANCELLED) {
      throw new ConflictException("Order has been cancelled, cannot confirm payment");
    }

    // Move the order into the intermediate CONFIRMING state BEFORE running the
    // saga. CONFIRMING blocks concurrent cancels from running between the saga's
    // hold-confirmations and the final PAID write, which would otherwise leave
    // Spanner reservations applied while the order is CANCELLED.
    boolean enteredConfirming =
        orderRepository.updateStatusIf(
            payment.getOrderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMING);
    if (!enteredConfirming) {
      throw new ConflictException(
          "Order " + payment.getOrderId() + " is not in PAYMENT_PENDING; confirm aborted");
    }

    try {
      // Saga throws ConflictException on partial failure (with compensation applied).
      paymentConfirmationSaga.confirm(payment, order);

      // Entitlement creation is intentionally OUTSIDE this try. The saga has
      // already confirmed inventory (money is effectively taken); rolling
      // back here would permanently consume that inventory while refunding
      // the user. See the post-saga block below for entitlement handling.

      // Only mark SUCCEEDED after all downstream effects complete.
      //
      // Cross-store consistency caveat: this method is @Transactional. The JPA
      // payment.setStatus + paymentRepository.save are buffered in the JPA
      // persistence context and only commit when this method returns. The
      // Spanner CAS below commits in its own transaction immediately. There is a
      // narrow window where the Spanner order is PAID but the JPA payment row is
      // still PROCESSING (e.g. JPA commit fails with a connection drop after the
      // Spanner CAS succeeds). PaymentReconciliationJob detects and repairs this
      // case by walking PAID orders whose payment rows remain in PROCESSING and
      // promoting them to SUCCEEDED.
      payment.setStatus(PaymentStatus.SUCCEEDED);
      paymentRepository.save(payment);
      boolean swapped =
          orderRepository.updateStatusIf(
              payment.getOrderId(), OrderStatus.CONFIRMING, OrderStatus.PAID);
      if (!swapped) {
        // Treat CAS-loss as a recoverable conflict so the catch block runs the
        // standard recovery path (rollback CONFIRMING→PAYMENT_PENDING, mark
        // payment FAILED). Throwing OUTSIDE the try previously leaked the
        // saga's confirmed inventory.
        throw new ConflictException(
            "Order " + payment.getOrderId() + " status changed concurrently; confirm aborted");
      }
    } catch (RuntimeException e) {
      // Saga, entitlement creation, or final CAS failed. Roll the order back to
      // PAYMENT_PENDING so the caller can retry. The post-saga CAS is now
      // included in this try so a deadline/network failure on it (which would
      // otherwise leave the order stuck in CONFIRMING with no recovery path)
      // is also handled here.
      boolean rolledBack =
          orderRepository.updateStatusIf(
              payment.getOrderId(), OrderStatus.CONFIRMING, OrderStatus.PAYMENT_PENDING);
      if (!rolledBack) {
        log.error(
            "Failed to roll back order {} from CONFIRMING to PAYMENT_PENDING after saga failure",
            payment.getOrderId());
      }
      // The outer @Transactional rolls back on `throw e`, so we cannot
      // persist the FAILED status here — it would be discarded with the
      // rest of the JPA transaction. Commit it independently in a new
      // transaction so the failure is durable across the rollback.
      markPaymentFailedInNewTransaction(payment.getPaymentId().toString());
      throw e;
    }

    // Entitlement creation runs AFTER the order is PAID and the payment is
    // SUCCEEDED. The saga has confirmed inventory, so the user has paid; if
    // entitlement creation fails we MUST NOT roll back inventory or the
    // payment, because the money is already taken. Entitlements are
    // idempotent on orderItemId, so a follow-up retry path can repair them.
    // TODO(phase-later): add an entitlement-recovery sweep job to
    // re-attempt creation for PAID orders missing entitlements.
    try {
      entitlementCreator.createEntitlementsForOrder(order);
    } catch (RuntimeException e) {
      log.error(
          "Entitlement creation failed for PAID order {} (payment {}); will require recovery",
          payment.getOrderId(),
          payment.getPaymentId(),
          e);
    }

    return payment;
  }

  @Override
  public Payment confirmByProviderPaymentId(String providerPaymentId) {
    Optional<Payment> maybe = paymentRepository.findByProviderPaymentId(providerPaymentId);
    if (maybe.isEmpty()) {
      log.warn(
          "Webhook confirm for unknown providerPaymentId={}; responding for retry",
          providerPaymentId);
      return null;
    }
    Payment payment = maybe.get();

    // Webhook-vs-AFTER_COMMIT race: Stripe can fire payment_intent.succeeded
    // before PaymentCreatedEventListener has advanced the order from PENDING
    // to PAYMENT_PENDING. Roll the order forward synchronously here so the
    // confirmPayment CAS (PAYMENT_PENDING -> CONFIRMING) does not fail.
    //
    // The CAS itself is idempotent: if the event listener already ran, this
    // is a no-op that returns false. We only care about the terminal state.
    try {
      Order order = orderRepository.findById(payment.getOrderId());
      if (order.status() == OrderStatus.PENDING) {
        boolean swapped =
            orderRepository.updateStatusIf(
                payment.getOrderId(), OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING);
        if (swapped) {
          log.info(
              "Rolled order {} PENDING->PAYMENT_PENDING synchronously for webhook {}",
              payment.getOrderId(),
              providerPaymentId);
        }
      }
    } catch (NotFoundException e) {
      log.warn("Webhook confirm: order {} not found", payment.getOrderId());
      return null;
    }

    return confirmPayment(payment.getPaymentId().toString());
  }

  @Override
  public Payment failByProviderPaymentId(String providerPaymentId) {
    Optional<Payment> maybe = paymentRepository.findByProviderPaymentId(providerPaymentId);
    if (maybe.isEmpty()) {
      log.warn("Webhook fail for unknown providerPaymentId={}; nothing to do", providerPaymentId);
      return null;
    }
    Payment payment = maybe.get();
    if (payment.getStatus() == PaymentStatus.FAILED
        || payment.getStatus() == PaymentStatus.SUCCEEDED) {
      return payment;
    }
    markPaymentFailedInNewTransaction(payment.getPaymentId().toString());
    // Roll the order back to PENDING so the user can retry the funnel.
    try {
      orderRepository.updateStatusIf(
          payment.getOrderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PENDING);
    } catch (RuntimeException ignored) {
      // benign — order may already be CANCELLED or in another state
    }
    return paymentRepository.findById(payment.getPaymentId()).orElse(payment);
  }

  /**
   * Persists a FAILED payment status in a new transaction so the write survives the rollback of the
   * calling {@code @Transactional} method. Uses a programmatic {@link TransactionTemplate} with
   * {@code PROPAGATION_REQUIRES_NEW} because a self-call to a {@code @Transactional} method would
   * bypass Spring's proxy and silently run in the existing transaction (which is about to roll
   * back).
   */
  private void markPaymentFailedInNewTransaction(String paymentId) {
    requiresNewTxTemplate.executeWithoutResult(
        status ->
            paymentRepository
                .findById(java.util.UUID.fromString(paymentId))
                .ifPresent(
                    p -> {
                      p.setStatus(PaymentStatus.FAILED);
                      paymentRepository.save(p);
                    }));
  }
}
