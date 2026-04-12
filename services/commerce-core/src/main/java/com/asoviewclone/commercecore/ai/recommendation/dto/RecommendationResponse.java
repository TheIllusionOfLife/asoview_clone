package com.asoviewclone.commercecore.ai.recommendation.dto;

import java.util.List;

public record RecommendationResponse(List<RecommendedProduct> products, String source) {

  public record RecommendedProduct(
      String productId, String name, String description, long minPriceJpy) {}
}
