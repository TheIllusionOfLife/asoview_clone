package com.asoviewclone.commercecore.reviews.repository;

import com.asoviewclone.commercecore.reviews.model.Review;
import com.asoviewclone.commercecore.reviews.model.ReviewStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  Optional<Review> findByUserIdAndProductId(UUID userId, UUID productId);

  Page<Review> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);

  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  /**
   * Atomic increment of {@code helpful_count} so concurrent votes do not lose updates via the usual
   * JPA read-modify-write race. The {@code ReviewHelpfulVote} unique constraint already gates
   * double-vote attempts; this method just keeps the denormalized counter in sync. (PR #21 review
   * H3 from Gemini.)
   */
  @Modifying
  @Query("UPDATE Review r SET r.helpfulCount = r.helpfulCount + 1 WHERE r.id = :reviewId")
  int incrementHelpfulCount(@Param("reviewId") UUID reviewId);

  @Modifying
  @Query(
      "UPDATE Review r SET r.helpfulCount = r.helpfulCount - 1"
          + " WHERE r.id = :reviewId AND r.helpfulCount > 0")
  int decrementHelpfulCount(@Param("reviewId") UUID reviewId);
}
