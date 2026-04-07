package com.asoviewclone.commercecore.points.discount;

import com.asoviewclone.commercecore.points.service.PointService;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies and refunds points-based discounts on orders. Soft-reference between Postgres discount
 * rows and the Spanner order (order_id as plain TEXT, no FK). All writes are single-store JPA so
 * standard @Transactional is sufficient.
 */
@Service
@Transactional
public class OrderDiscountService {

  public static final String DISCOUNT_TYPE_POINTS = "POINTS";

  private final OrderDiscountRepository discountRepository;
  private final PointService pointService;

  public OrderDiscountService(
      OrderDiscountRepository discountRepository, PointService pointService) {
    this.discountRepository = discountRepository;
    this.pointService = pointService;
  }

  /**
   * Apply a points-burn discount to a freshly created order. Idempotent via the unique(order_id)
   * constraint plus ledger (BURN_PURCHASE, orderId) check in PointService. Validates that
   * pointsToUse does not exceed min(currentBalance, subtotal).
   */
  public void applyPointsBurnDiscount(
      String orderId, UUID userId, long pointsToUse, BigDecimal subtotal) {
    if (pointsToUse <= 0) {
      return;
    }
    long subtotalLong = subtotal.longValue();
    long currentBalance = pointService.getBalance(userId);
    long maxUsable = Math.min(currentBalance, subtotalLong);
    if (pointsToUse > maxUsable) {
      throw new ValidationException(
          "pointsToUse="
              + pointsToUse
              + " exceeds min(balance="
              + currentBalance
              + ", subtotal="
              + subtotalLong
              + ")");
    }
    pointService.burn(userId, pointsToUse, orderId);
    // Idempotent insert: if a duplicate comes in due to retry, the unique(order_id)
    // constraint will prevent a second row; caller can catch and no-op.
    if (discountRepository.findByOrderId(orderId).isEmpty()) {
      discountRepository.save(new OrderDiscount(orderId, DISCOUNT_TYPE_POINTS, pointsToUse));
    }
  }

  /** Refund the burned points when an order is cancelled. Idempotent. */
  public void refundForCancelledOrder(String orderId, UUID userId) {
    Optional<OrderDiscount> existing = discountRepository.findByOrderId(orderId);
    if (existing.isEmpty()) {
      return;
    }
    OrderDiscount discount = existing.get();
    if (DISCOUNT_TYPE_POINTS.equals(discount.getDiscountType())) {
      pointService.refund(userId, discount.getAmountJpy(), orderId);
    }
    discountRepository.deleteByOrderId(orderId);
  }
}
