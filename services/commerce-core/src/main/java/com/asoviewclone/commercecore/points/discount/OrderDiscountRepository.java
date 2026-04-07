package com.asoviewclone.commercecore.points.discount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OrderDiscountRepository extends JpaRepository<OrderDiscount, UUID> {

  Optional<OrderDiscount> findByOrderId(String orderId);

  void deleteByOrderId(String orderId);

  /**
   * Returns rows older than {@code threshold} for the orphaned-discount sweep job. Used by {@code
   * OrphanedDiscountReconciliationJob} to find points-burn rows whose Spanner order either never
   * landed (process crashed mid-createOrder) or was rolled back.
   */
  @Query("SELECT d FROM OrderDiscount d WHERE d.createdAt < :threshold")
  List<OrderDiscount> findOlderThan(@Param("threshold") Instant threshold);

  /**
   * Atomic insert-or-noop for the discount row, returning the row count (1 = first writer for this
   * {@code order_id}, 0 = a previous writer already inserted it). Replaces the
   * findByOrderId-then-save TOCTOU pattern in {@code OrderDiscountService.applyPointsBurnDiscount}.
   * The {@code id} column is generated; the {@code order_id} is the unique key.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO order_discounts(id, order_id, discount_type, amount_jpy, created_at)"
              + " VALUES(gen_random_uuid(), :orderId, :discountType, :amountJpy, CURRENT_TIMESTAMP)"
              + " ON CONFLICT (order_id) DO NOTHING",
      nativeQuery = true)
  int insertIfMissing(
      @Param("orderId") String orderId,
      @Param("discountType") String discountType,
      @Param("amountJpy") long amountJpy);
}
