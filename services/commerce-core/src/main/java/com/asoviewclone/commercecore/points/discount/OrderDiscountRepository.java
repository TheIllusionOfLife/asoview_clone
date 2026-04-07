package com.asoviewclone.commercecore.points.discount;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
