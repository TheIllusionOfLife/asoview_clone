package com.asoviewclone.commercecore.points.event;

import com.asoviewclone.commercecore.orders.event.OrderPaidEvent;
import com.asoviewclone.commercecore.points.service.PointService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Credits points when an order is fully paid. Mirrors PaymentCreatedEventListener: AFTER_COMMIT
 * + @Retryable so failures here never roll back the payment transaction.
 *
 * <p>Earn rule: 1% of subtotal, floor.
 *
 * <p>Idempotent: PointServiceImpl checks (EARN_PURCHASE, orderId) in the ledger and no-ops.
 */
@Component
public class PointEarnListener {

  private static final Logger log = LoggerFactory.getLogger(PointEarnListener.class);

  private final PointService pointService;

  public PointEarnListener(PointService pointService) {
    this.pointService = pointService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000))
  public void onOrderPaid(OrderPaidEvent event) {
    long earned = event.subtotalJpy() / 100L; // floor(1%)
    if (earned <= 0) {
      return;
    }
    try {
      UUID userId = UUID.fromString(event.userId());
      pointService.earn(userId, earned, event.orderId());
    } catch (IllegalArgumentException e) {
      log.warn("OrderPaidEvent has non-UUID userId={}; skipping earn", event.userId());
    }
  }
}
