package com.asoviewclone.commercecore.reviews.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_helpful_votes")
@IdClass(ReviewHelpfulVoteId.class)
public class ReviewHelpfulVote {

  @Id
  @Column(name = "review_id")
  private UUID reviewId;

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "voted_at", nullable = false)
  private Instant votedAt = Instant.now();

  protected ReviewHelpfulVote() {}

  public ReviewHelpfulVote(UUID reviewId, UUID userId) {
    this.reviewId = reviewId;
    this.userId = userId;
  }

  public UUID getReviewId() {
    return reviewId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getVotedAt() {
    return votedAt;
  }
}
