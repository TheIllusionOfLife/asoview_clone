package com.asoviewclone.commercecore.reviews.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ReviewHelpfulVoteId implements Serializable {

  private UUID reviewId;
  private UUID userId;

  public ReviewHelpfulVoteId() {}

  public ReviewHelpfulVoteId(UUID reviewId, UUID userId) {
    this.reviewId = reviewId;
    this.userId = userId;
  }

  public UUID getReviewId() {
    return reviewId;
  }

  public UUID getUserId() {
    return userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReviewHelpfulVoteId that)) return false;
    return Objects.equals(reviewId, that.reviewId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(reviewId, userId);
  }
}
