package com.asoviewclone.commercecore.catalog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_review_aggregates")
public class ProductReviewAggregate {

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "average_rating", nullable = false)
  private BigDecimal averageRating = BigDecimal.ZERO;

  @Column(name = "review_count", nullable = false)
  private int reviewCount = 0;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected ProductReviewAggregate() {}

  public UUID getProductId() {
    return productId;
  }

  public BigDecimal getAverageRating() {
    return averageRating;
  }

  public int getReviewCount() {
    return reviewCount;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
