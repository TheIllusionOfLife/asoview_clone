package com.asoviewclone.commercecore.reviews.service;

import com.asoviewclone.commercecore.reviews.model.Review;
import com.asoviewclone.commercecore.reviews.model.ReviewHelpfulVote;
import com.asoviewclone.commercecore.reviews.model.ReviewStatus;
import com.asoviewclone.commercecore.reviews.repository.ReviewHelpfulVoteRepository;
import com.asoviewclone.commercecore.reviews.repository.ReviewRepository;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.ForbiddenException;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReviewServiceImpl implements ReviewService {

  private final ReviewRepository reviewRepository;
  private final ReviewHelpfulVoteRepository voteRepository;
  private final ReviewEligibilityService eligibilityService;

  public ReviewServiceImpl(
      ReviewRepository reviewRepository,
      ReviewHelpfulVoteRepository voteRepository,
      ReviewEligibilityService eligibilityService) {
    this.reviewRepository = reviewRepository;
    this.voteRepository = voteRepository;
    this.eligibilityService = eligibilityService;
  }

  @Override
  public Review createReview(
      UUID userId, UUID productId, short rating, String title, String body, String language) {
    if (rating < 1 || rating > 5) {
      throw new ValidationException("rating must be between 1 and 5");
    }
    if (title == null || title.isBlank() || body == null || body.isBlank()) {
      throw new ValidationException("title and body are required");
    }
    if (!eligibilityService.existsPaidOrderForUserAndProduct(userId.toString(), productId)) {
      throw new ForbiddenException(
          "User has no paid order for product " + productId + "; cannot review");
    }
    if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
      throw new ConflictException(
          "Review already exists for user " + userId + " and product " + productId);
    }
    Review review = new Review(userId, productId, rating, title, body);
    if (language != null && !language.isBlank()) {
      review.setLanguage(language);
    }
    return reviewRepository.save(review);
  }

  @Override
  public Review updateReview(UUID userId, UUID reviewId, Short rating, String title, String body) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new NotFoundException("Review", reviewId.toString()));
    if (!review.getUserId().equals(userId)) {
      throw new ForbiddenException("User may only edit their own reviews");
    }
    if (review.getStatus() == ReviewStatus.DELETED) {
      throw new ConflictException("Cannot update deleted review");
    }
    if (rating != null) {
      if (rating < 1 || rating > 5) {
        throw new ValidationException("rating must be between 1 and 5");
      }
      review.setRating(rating);
    }
    if (title != null) {
      review.setTitle(title);
    }
    if (body != null) {
      review.setBody(body);
    }
    return reviewRepository.save(review);
  }

  @Override
  public void deleteReview(UUID userId, UUID reviewId) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new NotFoundException("Review", reviewId.toString()));
    if (!review.getUserId().equals(userId)) {
      throw new ForbiddenException("User may only delete their own reviews");
    }
    review.setStatus(ReviewStatus.DELETED);
    reviewRepository.save(review);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<Review> listByProduct(UUID productId, Pageable pageable) {
    return reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED, pageable);
  }

  @Override
  public void voteHelpful(UUID userId, UUID reviewId) {
    Review review =
        reviewRepository
            .findById(reviewId)
            .orElseThrow(() -> new NotFoundException("Review", reviewId.toString()));
    if (voteRepository.existsByReviewIdAndUserId(reviewId, userId)) {
      return; // idempotent
    }
    voteRepository.save(new ReviewHelpfulVote(reviewId, userId));
    review.setHelpfulCount(review.getHelpfulCount() + 1);
    reviewRepository.save(review);
  }
}
