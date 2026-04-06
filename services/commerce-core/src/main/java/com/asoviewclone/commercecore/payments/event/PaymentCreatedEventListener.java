package com.asoviewclone.commercecore.payments.event;

import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After the payment JPA transaction commits, CAS the order from PENDING to PAYMENT_PENDING. If all
 * retries are exhausted, the payment row still exists and the caller can retry, so we simply log
 * and let the order stay PENDING instead of amplifying the failure.
 */
@Component
public class PaymentCreatedEventListener {

  private static final Logger log = LoggerFactory.getLogger(PaymentCreatedEventListener.class);

  private final OrderRepository orderRepository;

  public PaymentCreatedEventListener(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000))
  public void onPaymentCreated(PaymentCreatedEvent event) {
    boolean swapped =
        orderRepository.updateStatusIf(
            event.orderId(), OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING);
    if (!swapped) {
      // Order is already past PENDING (cancelled, already advanced, etc.). Not an error.
      log.warn(
          "Order {} was not in PENDING when advancing to PAYMENT_PENDING; leaving as-is",
          event.orderId());
    }
  }
}
