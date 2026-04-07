package com.asoviewclone.commercecore.reviews.repository;

import com.asoviewclone.commercecore.reviews.model.Review;
import com.asoviewclone.commercecore.reviews.model.ReviewStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  Optional<Review> findByUserIdAndProductId(UUID userId, UUID productId);

  Page<Review> findByProductIdAndStatus(UUID productId, ReviewStatus status, Pageable pageable);

  boolean existsByUserIdAndProductId(UUID userId, UUID productId);
}
