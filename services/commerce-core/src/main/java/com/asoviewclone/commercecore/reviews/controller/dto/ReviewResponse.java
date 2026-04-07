package com.asoviewclone.commercecore.reviews.controller.dto;

import com.asoviewclone.commercecore.reviews.model.Review;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID userId,
    UUID productId,
    short rating,
    String title,
    String body,
    String language,
    String status,
    int helpfulCount,
    Instant createdAt,
    Instant updatedAt) {

  public static ReviewResponse from(Review r) {
    return new ReviewResponse(
        r.getId(),
        r.getUserId(),
        r.getProductId(),
        r.getRating(),
        r.getTitle(),
        r.getBody(),
        r.getLanguage(),
        r.getStatus().name(),
        r.getHelpfulCount(),
        r.getAudit().getCreatedAt(),
        r.getAudit().getUpdatedAt());
  }
}
