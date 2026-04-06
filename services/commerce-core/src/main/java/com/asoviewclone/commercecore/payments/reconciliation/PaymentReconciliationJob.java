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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

  public PaymentReconciliationJob(
      PaymentRepository paymentRepository, OrderRepository orderRepository) {
    this.paymentRepository = paymentRepository;
    this.orderRepository = orderRepository;
  }

  /** Maximum payments inspected per run. Bounded so the job stays within a short transaction. */
  private static final int BATCH_SIZE = 100;

  @Scheduled(fixedDelay = 300_000)
  @Transactional
  public void reconcileProcessingPayments() {
    Pageable batch = PageRequest.of(0, BATCH_SIZE);
    List<Payment> processing = paymentRepository.findByStatus(PaymentStatus.PROCESSING, batch);
    if (processing.isEmpty()) {
      return;
    }
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
        }
        // For PENDING/PAYMENT_PENDING/CONFIRMING/REFUNDED, leave the payment alone — the
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
