package com.asoviewclone.commercecore.points.event;

import com.asoviewclone.commercecore.orders.event.OrderCancelledEvent;
import com.asoviewclone.commercecore.points.discount.OrderDiscountService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refunds any points-based discount on an order when the order is cancelled. AFTER_COMMIT so the
 * refund never rolls back the cancel transaction. Idempotent via the (REFUND_CANCEL, orderId)
 * ledger guard in PointService.
 */
@Component
public class PointRefundListener {

  private static final Logger log = LoggerFactory.getLogger(PointRefundListener.class);

  private final OrderDiscountService orderDiscountService;

  public PointRefundListener(OrderDiscountService orderDiscountService) {
    this.orderDiscountService = orderDiscountService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 200, multiplier = 2.0, maxDelay = 2000))
  public void onOrderCancelled(OrderCancelledEvent event) {
    try {
      UUID userId = UUID.fromString(event.userId());
      orderDiscountService.refundForCancelledOrder(event.orderId(), userId);
    } catch (IllegalArgumentException e) {
      log.warn(
          "OrderCancelledEvent has non-UUID userId={}; skipping points refund", event.userId());
    }
  }
}
