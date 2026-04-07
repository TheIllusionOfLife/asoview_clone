package com.asoviewclone.commercecore.reviews.repository;

import com.asoviewclone.commercecore.reviews.model.ReviewHelpfulVote;
import com.asoviewclone.commercecore.reviews.model.ReviewHelpfulVoteId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulVoteRepository
    extends JpaRepository<ReviewHelpfulVote, ReviewHelpfulVoteId> {

  boolean existsByReviewIdAndUserId(java.util.UUID reviewId, java.util.UUID userId);
}
