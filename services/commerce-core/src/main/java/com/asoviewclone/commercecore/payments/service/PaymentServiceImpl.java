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

    // Verify order ownership. Throw NotFoundException (404) — not ValidationException (400) —
    // to match the GET /v1/orders/{id} non-enumeration policy: callers must not be able to
    // distinguish "order does not exist" from "order exists but belongs to someone else".
    if (!order.userId().equals(userId)) {
      throw new NotFoundException("Order", orderId);
    }

    if (!order.status().canTransitionTo(OrderStatus.PAYMENT_PENDING)) {
      throw new ValidationException("Cannot create payment for order in status " + order.status());
    }

    Payment payment =
        new Payment(
            orderId, userId, new BigDecimal(order.totalAmount()), order.currency(), idempotencyKey);

    PaymentGateway.PaymentResult result =
        paymentGateway.createIntent(
            orderId, payment.getAmount(), payment.getCurrency(), idempotencyKey);

    if (!result.success()) {
      throw new ConflictException("Payment gateway rejected the request");
    }

    payment.setProvider(paymentGateway.providerName());
    payment.setProviderPaymentId(result.providerPaymentId());
    // Persist clientSecret on the row so the idempotent replay path above can return it
    // verbatim without re-fetching from the provider — keeps the replay offline-safe.
    payment.setClientSecret(result.clientSecret());
    payment.setRedirectUrl(result.redirectUrl());
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
    eventPublisher.publishEvent(
        new PaymentCreatedEvent(
            orderId,
            saved.getPaymentId().toString(),
            saved.getAmount().setScale(0, java.math.RoundingMode.HALF_UP).longValueExact(),
            saved.getProvider()));

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
      // Saga, entitlement creation, or final CAS failed. Try to roll the order
      // back to PAYMENT_PENDING so the caller can retry.
      boolean rolledBack =
          orderRepository.updateStatusIf(
              payment.getOrderId(), OrderStatus.CONFIRMING, OrderStatus.PAYMENT_PENDING);
      if (rolledBack) {
        // Clean failure: the order is back in PAYMENT_PENDING. Persist
        // the FAILED payment status in a new transaction so the write
        // survives the outer @Transactional rollback triggered by `throw e`.
        markPaymentFailedInNewTransaction(payment.getPaymentId().toString());
      } else {
        // Rollback CAS lost the race, meaning the order is NOT in CONFIRMING
        // anymore. The most common cause is a post-saga CONFIRMING -> PAID CAS
        // that already committed on Spanner but the client saw a network
        // timeout: the saga has actually succeeded, inventory is confirmed,
        // and the order is PAID. Marking the payment FAILED here would be
        // wrong — the money was taken and the user has their reservation.
        //
        // Instead, let the outer @Transactional roll back. The in-memory
        // SUCCEEDED write on `payment` is discarded with the rollback and
        // the row on disk stays PROCESSING. PaymentReconciliationJob sweeps
        // PROCESSING payments, walks the order state, and promotes the row
        // to SUCCEEDED when it sees the order is PAID. For any other
        // unexpected terminal state (CANCELLED, REFUNDED) the reconciliation
        // job promotes the payment to FAILED the same way.
        log.error(
            "Rollback CAS failed for order {} after saga failure; leaving payment in PROCESSING for PaymentReconciliationJob to repair",
            payment.getOrderId());
      }
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

    // Publish OrderPaidEvent so AFTER_COMMIT listeners (e.g. points earn-on-PAID) can
    // run after the JPA transaction commits. The event is decoupled from the saga +
    // entitlement creation: any listener exception will not roll back PAID state.
    //
    // unit_price is stored as a NUMERIC(12,2) string ("1500.00"), so it MUST be parsed
    // via BigDecimal — Long.parseLong throws NumberFormatException on the trailing
    // ".00" and silently zeroes the subtotal (PR #21 review C2 from Devin).
    long subtotalJpy = 0L;
    for (com.asoviewclone.commercecore.orders.model.OrderItem item : order.items()) {
      try {
        long unit = new java.math.BigDecimal(item.unitPrice()).longValueExact();
        subtotalJpy += unit * item.quantity();
      } catch (NumberFormatException | ArithmeticException nfe) {
        log.warn(
            "Order {} item {} unit_price '{}' is not parseable as integer JPY; treating as 0 for points calc",
            order.orderId(),
            item.orderItemId(),
            item.unitPrice());
      }
    }
    eventPublisher.publishEvent(
        new com.asoviewclone.commercecore.orders.event.OrderPaidEvent(
            order.orderId(), order.userId(), subtotalJpy));

    return payment;
  }

  @Override
  public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
    return paymentRepository.findByProviderPaymentId(providerPaymentId);
  }

  @Override
  public boolean isTerminalConflict(String providerPaymentId) {
    Optional<Payment> maybe = paymentRepository.findByProviderPaymentId(providerPaymentId);
    if (maybe.isEmpty()) {
      return false;
    }
    Payment p = maybe.get();
    if (p.getStatus() == PaymentStatus.SUCCEEDED || p.getStatus() == PaymentStatus.FAILED) {
      return true;
    }
    try {
      Order order = orderRepository.findById(p.getOrderId());
      // A cancelled / paid / refunded order can never transition back into
      // PAYMENT_PENDING, so any retry of the same webhook is doomed.
      return order.status() == OrderStatus.CANCELLED
          || order.status() == OrderStatus.PAID
          || order.status() == OrderStatus.REFUNDED;
    } catch (NotFoundException e) {
      return false;
    }
  }

  @Override
  @Transactional
  public Payment confirmByProviderPaymentId(String providerPaymentId) {
    // @Transactional is critical here: confirmByProviderPaymentId self-invokes
    // confirmPayment, which would bypass the Spring proxy and run without a
    // transaction if this method were not itself transactional. The proxy
    // opens the tx on the external entry point; the self-call then runs
    // inside that same tx (Spring's @Transactional on confirmPayment would
    // otherwise be ignored on a self-call).
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
  @Transactional
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

    // Out-of-order webhook guard: Stripe can deliver payment_intent.payment_failed
    // AFTER a payment_intent.succeeded has already driven the order to PAID (or
    // driven the saga to CONFIRMING). Overwriting the payment to FAILED in those
    // cases would corrupt state: order=PAID with payment=FAILED is unrecoverable
    // because PaymentReconciliationJob only sweeps PROCESSING rows.
    //
    // Only mark FAILED when the order is still in a pre-confirm state.
    try {
      Order order = orderRepository.findById(payment.getOrderId());
      if (order.status() == OrderStatus.PAID
          || order.status() == OrderStatus.CONFIRMING
          || order.status() == OrderStatus.REFUNDED) {
        log.warn(
            "Ignoring out-of-order FAILED webhook for payment {}: order {} is {}",
            payment.getPaymentId(),
            payment.getOrderId(),
            order.status());
        return payment;
      }
    } catch (NotFoundException e) {
      log.warn("Webhook fail: order {} not found", payment.getOrderId());
      return null;
    }

    // IMPORTANT: order rollback runs BEFORE the payment FAILED CAS. If the
    // rollback fails and we have already written payment=FAILED, we end up with
    // payment=FAILED / order=PAYMENT_PENDING — a TERMINALLY stuck state because
    // OrderStatus.PAYMENT_PENDING can only transition to CONFIRMING or
    // CANCELLED, and PaymentReconciliationJob only sweeps PROCESSING rows.
    //
    // Doing the order rollback first means: if the rollback fails, the payment
    // stays PROCESSING on disk and the next reconciliation sweep (or another
    // webhook delivery) can repair the state. We accept a brief window where
    // the order is PENDING but the payment is still PROCESSING — the user
    // simply cannot create a second payment until the partial unique index
    // releases, which mirrors normal retry behavior.
    try {
      orderRepository.updateStatusIf(
          payment.getOrderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PENDING);
    } catch (RuntimeException ignored) {
      // benign — order may already be CANCELLED or in another state
    }

    // CAS from PROCESSING -> FAILED. Hardcode the expected status (instead of
    // observing payment.getStatus() inside the new transaction) so a concurrent
    // success writer that already flipped the row to SUCCEEDED cannot be
    // overwritten back to FAILED. If we lose the race, return the current
    // payment row.
    Integer updated =
        requiresNewTxTemplate.execute(
            status ->
                paymentRepository.updateStatusIf(
                    payment.getPaymentId(), PaymentStatus.PROCESSING, PaymentStatus.FAILED));
    if (updated == null || updated == 0) {
      return paymentRepository.findById(payment.getPaymentId()).orElse(payment);
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
                      // Hardcode the expected status to PROCESSING (the only
                      // pre-terminal state this rollback path is allowed to
                      // downgrade from). Reading p.getStatus() here is unsafe
                      // because a concurrent confirmPayment may have already
                      // flushed SUCCEEDED — using p.getStatus() as the CAS
                      // expected value would then match and overwrite
                      // SUCCEEDED -> FAILED, corrupting a legitimate success.
                      paymentRepository.updateStatusIf(
                          p.getPaymentId(), PaymentStatus.PROCESSING, PaymentStatus.FAILED);
                    }));
  }
}
