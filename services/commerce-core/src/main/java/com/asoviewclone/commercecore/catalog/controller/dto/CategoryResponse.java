package com.asoviewclone.commercecore.catalog.controller.dto;

import com.asoviewclone.commercecore.catalog.model.Category;
import java.util.UUID;

public record CategoryResponse(
    UUID id, String name, String slug, UUID parentId, int displayOrder, String imageUrl) {

  public static CategoryResponse from(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getSlug(),
        category.getParentId(),
        category.getDisplayOrder(),
        category.getImageUrl());
  }
}
