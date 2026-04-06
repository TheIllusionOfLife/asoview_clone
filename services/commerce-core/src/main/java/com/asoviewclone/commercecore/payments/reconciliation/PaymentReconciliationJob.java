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

  @Scheduled(fixedDelay = 300_000)
  @Transactional
  public void reconcileProcessingPayments() {
    List<Payment> processing = paymentRepository.findByStatus(PaymentStatus.PROCESSING);
    if (processing.isEmpty()) {
      return;
    }
    for (Payment payment : processing) {
      try {
        Order order = orderRepository.findById(payment.getOrderId());
        if (order.status() == OrderStatus.PAID) {
          log.warn(
              "Reconciling divergent payment {}: order {} is PAID, promoting payment to SUCCEEDED",
              payment.getPaymentId(),
              payment.getOrderId());
          payment.setStatus(PaymentStatus.SUCCEEDED);
          paymentRepository.save(payment);
        } else if (order.status() == OrderStatus.CANCELLED) {
          log.warn(
              "Reconciling divergent payment {}: order {} is CANCELLED, marking payment FAILED",
              payment.getPaymentId(),
              payment.getOrderId());
          payment.setStatus(PaymentStatus.FAILED);
          paymentRepository.save(payment);
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
