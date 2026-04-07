package com.asoviewclone.commercecore.payments.reconciliation;

import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentStatus;
import com.asoviewclone.commercecore.payments.repository.PaymentRepository;
import com.asoviewclone.common.error.NotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconciles cross-store divergence between Cloud SQL {@code payments} and Spanner {@code orders}.
 *
 * <p>{@code PaymentServiceImpl.confirmPayment} writes the JPA payment status and the Spanner order
 * status in the same {@code @Transactional} method, but JPA commits at method exit while the
 * Spanner CAS commits immediately. A JPA commit failure after a successful Spanner CAS leaves the
 * order in {@code PAID} while the payment row remains in {@code PROCESSING}. This job sweeps {@code
 * PROCESSING} payments, checks the corresponding order, and promotes the payment to {@code
 * SUCCEEDED} when the order is {@code PAID}.
 *
 * <p>Runs every 5 minutes; deliberately conservative cadence so it does not contend with the
 * happy-path confirm flow.
 */
@Component
public class PaymentReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

  private final PaymentRepository paymentRepository;
  private final OrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final TransactionTemplate requiresNewTxTemplate;

  public PaymentReconciliationJob(
      PaymentRepository paymentRepository,
      OrderRepository orderRepository,
      ApplicationEventPublisher eventPublisher,
      PlatformTransactionManager transactionManager) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
    this.eventPublisher = eventPublisher;
    this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTxTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Page size for each batch. */
  private static final int BATCH_SIZE = 100;

  /** Hard upper bound on payments inspected per scheduled run. */
  private static final int MAX_PER_RUN = 1000;

  @Scheduled(fixedDelay = 300_000)
  public void reconcileProcessingPayments() {
    int processed = 0;
    while (processed < MAX_PER_RUN) {
      // Order by paymentId ascending for a stable, deterministic sweep order
      // (Payment has no createdAt column). This ensures stragglers past the
      // first page are not starved across runs. Each
      // iteration re-queries page 0 because processed rows transition OUT of
      // PROCESSING and would shift the offset for a fixed pageIndex.
      // Untouched rows (no divergence to repair) act as a natural cursor: if
      // the batch returns the same untouched rows twice we exit to avoid a
      // tight loop.
      Pageable batch = PageRequest.of(0, BATCH_SIZE, Sort.by("paymentId").ascending());
      List<Payment> processing = paymentRepository.findByStatus(PaymentStatus.PROCESSING, batch);
      if (processing.isEmpty()) {
        return;
      }
      // Each batch runs in its own short transaction (REQUIRES_NEW) so a long
      // sweep does not hold one giant transaction across all pages.
      int sizeBefore = processing.size();
      requiresNewTxTemplate.executeWithoutResult(status -> reconcileBatch(processing));
      processed += sizeBefore;
      if (sizeBefore < BATCH_SIZE) {
        return;
      }
      // Re-fetch to see whether any of the rows we just processed transitioned
      // out of PROCESSING. If none did (all were left in PROCESSING because
      // their order was in PAYMENT_PENDING/CONFIRMING/etc.), we have nothing
      // more to do this run.
      List<Payment> after = paymentRepository.findByStatus(PaymentStatus.PROCESSING, batch);
      if (after.size() == sizeBefore && sameIds(after, processing)) {
        return;
      }
    }
  }

  private static long computeSubtotalJpy(Order order) {
    long subtotal = 0L;
    for (com.asoviewclone.commercecore.orders.model.OrderItem item : order.items()) {
      try {
        long unit = new java.math.BigDecimal(item.unitPrice()).longValueExact();
        subtotal += unit * item.quantity();
      } catch (NumberFormatException | ArithmeticException ex) {
        // Mirror PaymentServiceImpl: log so a zero subtotal is visible in
        // ops dashboards instead of silently producing zero points.
        log.warn(
            "Reconciliation: order {} item {} unit_price '{}' is not parseable as integer JPY;"
                + " treating as 0 for points calc",
            order.orderId(),
            item.orderItemId(),
            item.unitPrice());
      }
    }
    return subtotal;
  }

  private static boolean sameIds(List<Payment> a, List<Payment> b) {
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).getPaymentId().equals(b.get(i).getPaymentId())) {
        return false;
      }
    }
    return true;
  }

  // Propagation.MANDATORY: this method is always invoked inside the
  // per-batch requiresNewTxTemplate.executeWithoutResult(...) scope; the
  // annotation asserts that contract at runtime AND satisfies the
  // EventPublisherRules ArchUnit check (which requires every
  // publishEvent caller to be @Transactional). See PR 3d.5.
  @Transactional(propagation = Propagation.MANDATORY)
  public void reconcileBatch(List<Payment> processing) {
    for (Payment payment : processing) {
      try {
        Order order = orderRepository.findById(payment.getOrderId());
        if (order.status() == OrderStatus.PAID) {
          // CAS PROCESSING→SUCCEEDED. If a concurrent confirmPayment already
          // wrote SUCCEEDED (or any other terminal status), the update count
          // is 0 and we leave the row alone — no last-writer-wins overwrite.
          int updated =
              paymentRepository.updateStatusIf(
                  payment.getPaymentId(), PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED);
          if (updated == 1) {
            log.warn(
                "Reconciled divergent payment {}: order {} is PAID, promoted PROCESSING→SUCCEEDED",
                payment.getPaymentId(),
                payment.getOrderId());
            // Re-publish OrderPaidEvent so AFTER_COMMIT side effects (points
            // earn-on-PAID, future analytics) catch up on orders that were
            // recovered through this divergence-repair path. PaymentServiceImpl's
            // happy-path publish was skipped because the JPA tx rolled back.
            // (PR #21 Codex finding: recovered orders never earned points.)
            long subtotalJpy = computeSubtotalJpy(order);
            eventPublisher.publishEvent(
                new com.asoviewclone.commercecore.orders.event.OrderPaidEvent(
                    order.orderId(), order.userId(), subtotalJpy));
          }
        } else if (order.status() == OrderStatus.CANCELLED) {
          int updated =
              paymentRepository.updateStatusIf(
                  payment.getPaymentId(), PaymentStatus.PROCESSING, PaymentStatus.FAILED);
          if (updated == 1) {
            log.warn(
                "Reconciled divergent payment {}: order {} is CANCELLED, marked PROCESSING→FAILED",
                payment.getPaymentId(),
                payment.getOrderId());
          }
        } else if (order.status() == OrderStatus.PENDING) {
          // The AFTER_COMMIT listener that normally advances PENDING→PAYMENT_PENDING
          // has exhausted its retries, leaving the payment PROCESSING and the order
          // PENDING with no recovery path. Repair it by performing the same CAS
          // the listener would have performed.
          boolean advanced =
              orderRepository.updateStatusIf(
                  payment.getOrderId(), OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING);
          if (advanced) {
            log.warn(
                "Reconciled stuck payment {}: order {} advanced PENDING→PAYMENT_PENDING",
                payment.getPaymentId(),
                payment.getOrderId());
          }
        }
        // For PAYMENT_PENDING/CONFIRMING/REFUNDED, leave the payment alone — the
        // confirmPayment flow may still be in progress, or the divergence is the other
        // direction (Spanner behind JPA), which is harmless.
      } catch (NotFoundException nfe) {
        log.error(
            "Reconciliation: payment {} references missing order {}",
            payment.getPaymentId(),
            payment.getOrderId(),
            nfe);
      } catch (Exception e) {
        log.warn(
            "Reconciliation failed for payment {}; will retry next run", payment.getPaymentId(), e);
      }
    }
  }
}
