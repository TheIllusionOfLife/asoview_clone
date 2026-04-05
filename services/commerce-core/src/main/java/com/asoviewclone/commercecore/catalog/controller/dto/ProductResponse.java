package com.asoviewclone.commercecore.catalog.controller.dto;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    UUID tenantId,
    UUID venueId,
    UUID categoryId,
    String title,
    String description,
    String imageUrl,
    String status,
    List<VariantResponse> variants) {

  public static ProductResponse from(Product product) {
    List<VariantResponse> variants =
        product.getVariants().stream().map(VariantResponse::from).toList();
    return new ProductResponse(
        product.getId(),
        product.getTenantId(),
        product.getVenueId(),
        product.getCategoryId(),
        product.getTitle(),
        product.getDescription(),
        product.getImageUrl(),
        product.getStatus().name(),
        variants);
  }

  public record VariantResponse(
      UUID id,
      String name,
      BigDecimal priceAmount,
      String priceCurrency,
      Integer durationMinutes,
      Integer maxParticipants) {

    public static VariantResponse from(ProductVariant variant) {
      return new VariantResponse(
          variant.getId(),
          variant.getName(),
          variant.getPriceAmount(),
          variant.getPriceCurrency(),
          variant.getDurationMinutes(),
          variant.getMaxParticipants());
    }
  }
}
