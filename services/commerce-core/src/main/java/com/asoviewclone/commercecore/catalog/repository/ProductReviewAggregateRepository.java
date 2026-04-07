package com.asoviewclone.commercecore.catalog.repository;

import com.asoviewclone.commercecore.catalog.model.ProductReviewAggregate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductReviewAggregateRepository
    extends JpaRepository<ProductReviewAggregate, UUID> {

  List<ProductReviewAggregate> findByProductIdIn(Collection<UUID> productIds);

  /**
   * Recompute aggregates across all products in a single SQL statement. Uses Postgres upsert to
   * avoid N+1. Only counts PUBLISHED reviews.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO product_review_aggregates (product_id, average_rating, review_count, updated_at) "
              + "SELECT product_id, COALESCE(AVG(rating)::numeric(3,2), 0), COUNT(*), now() "
              + "FROM reviews WHERE status = 'PUBLISHED' GROUP BY product_id "
              + "ON CONFLICT (product_id) DO UPDATE SET "
              + "average_rating = EXCLUDED.average_rating, "
              + "review_count = EXCLUDED.review_count, "
              + "updated_at = EXCLUDED.updated_at",
      nativeQuery = true)
  int recomputeAll();
}
