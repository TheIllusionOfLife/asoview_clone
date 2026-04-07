package com.asoviewclone.commercecore.reviews.service;

import com.asoviewclone.commercecore.reviews.model.Review;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

  Review createReview(
      UUID userId, UUID productId, short rating, String title, String body, String language);

  Review updateReview(UUID userId, UUID reviewId, Short rating, String title, String body);

  void deleteReview(UUID userId, UUID reviewId);

  Page<Review> listByProduct(UUID productId, Pageable pageable);

  void voteHelpful(UUID userId, UUID reviewId);
}
