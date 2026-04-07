package com.asoviewclone.commercecore.reviews.controller.dto;

import java.util.UUID;

public record CreateReviewRequest(
    UUID productId, short rating, String title, String body, String language) {}
