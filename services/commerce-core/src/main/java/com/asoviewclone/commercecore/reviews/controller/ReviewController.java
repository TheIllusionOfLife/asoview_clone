package com.asoviewclone.commercecore.reviews.controller;

import com.asoviewclone.commercecore.reviews.controller.dto.CreateReviewRequest;
import com.asoviewclone.commercecore.reviews.controller.dto.ReviewResponse;
import com.asoviewclone.commercecore.reviews.controller.dto.UpdateReviewRequest;
import com.asoviewclone.commercecore.reviews.service.ReviewService;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class ReviewController {

  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }

  @PostMapping("/reviews")
  @ResponseStatus(HttpStatus.CREATED)
  public ReviewResponse createReview(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestBody CreateReviewRequest request) {
    return ReviewResponse.from(
        reviewService.createReview(
            user.userId(),
            request.productId(),
            request.rating(),
            request.title(),
            request.body(),
            request.language()));
  }

  @PatchMapping("/reviews/{reviewId}")
  public ReviewResponse updateReview(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable UUID reviewId,
      @RequestBody UpdateReviewRequest request) {
    return ReviewResponse.from(
        reviewService.updateReview(
            user.userId(), reviewId, request.rating(), request.title(), request.body()));
  }

  @DeleteMapping("/reviews/{reviewId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteReview(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reviewId) {
    reviewService.deleteReview(user.userId(), reviewId);
  }

  @GetMapping("/products/{productId}/reviews")
  public Page<ReviewResponse> listProductReviews(
      @PathVariable UUID productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return reviewService
        .listByProduct(productId, PageRequest.of(page, size))
        .map(ReviewResponse::from);
  }

  @PostMapping("/reviews/{reviewId}/helpful")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void voteHelpful(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID reviewId) {
    reviewService.voteHelpful(user.userId(), reviewId);
  }
}
