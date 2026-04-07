package com.asoviewclone.commercecore.reviews.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.reviews.model.Review;
import com.asoviewclone.commercecore.reviews.model.ReviewHelpfulVote;
import com.asoviewclone.commercecore.reviews.model.ReviewStatus;
import com.asoviewclone.commercecore.reviews.repository.ReviewHelpfulVoteRepository;
import com.asoviewclone.commercecore.reviews.repository.ReviewRepository;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.error.ForbiddenException;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewServiceImplTest {

  private ReviewRepository reviewRepository;
  private ReviewHelpfulVoteRepository voteRepository;
  private ReviewEligibilityService eligibilityService;
  private ReviewServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final UUID otherUserId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID reviewId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    reviewRepository = mock(ReviewRepository.class);
    voteRepository = mock(ReviewHelpfulVoteRepository.class);
    eligibilityService = mock(ReviewEligibilityService.class);
    service = new ReviewServiceImpl(reviewRepository, voteRepository, eligibilityService);
  }

  @Test
  void createReview_happy() {
    when(eligibilityService.existsPaidOrderForUserAndProduct(anyString(), eq(productId)))
        .thenReturn(true);
    when(reviewRepository.existsByUserIdAndProductId(userId, productId)).thenReturn(false);
    when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

    Review r = service.createReview(userId, productId, (short) 5, "Great", "Loved it", "ja");

    assertThat(r.getRating()).isEqualTo((short) 5);
    assertThat(r.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
    verify(reviewRepository).save(any(Review.class));
  }

  @Test
  void createReview_withoutPaidOrder_throwsForbidden() {
    when(eligibilityService.existsPaidOrderForUserAndProduct(anyString(), eq(productId)))
        .thenReturn(false);

    assertThatThrownBy(() -> service.createReview(userId, productId, (short) 4, "t", "b", "ja"))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void createReview_duplicate_throwsConflict() {
    when(eligibilityService.existsPaidOrderForUserAndProduct(anyString(), eq(productId)))
        .thenReturn(true);
    when(reviewRepository.existsByUserIdAndProductId(userId, productId)).thenReturn(true);

    assertThatThrownBy(() -> service.createReview(userId, productId, (short) 4, "t", "b", "ja"))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void createReview_invalidRating_throwsValidation() {
    assertThatThrownBy(() -> service.createReview(userId, productId, (short) 6, "t", "b", "ja"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void updateReview_nonOwner_throwsForbidden() {
    Review existing = new Review(otherUserId, productId, (short) 3, "t", "b");
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.updateReview(userId, reviewId, (short) 5, "x", "y"))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void deleteReview_softDeletes() {
    Review existing = new Review(userId, productId, (short) 3, "t", "b");
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existing));
    when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

    service.deleteReview(userId, reviewId);

    assertThat(existing.getStatus()).isEqualTo(ReviewStatus.DELETED);
    verify(reviewRepository).save(existing);
  }

  @Test
  void deleteReview_notFound_throwsNotFound() {
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteReview(userId, reviewId))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void voteHelpful_idempotent() {
    when(reviewRepository.existsById(reviewId)).thenReturn(true);
    when(voteRepository.existsByReviewIdAndUserId(reviewId, userId)).thenReturn(false, true);
    when(reviewRepository.incrementHelpfulCount(reviewId)).thenReturn(1);

    service.voteHelpful(userId, reviewId);
    service.voteHelpful(userId, reviewId);

    verify(voteRepository, times(1)).save(any(ReviewHelpfulVote.class));
    verify(reviewRepository, times(1)).incrementHelpfulCount(reviewId);
  }
}
