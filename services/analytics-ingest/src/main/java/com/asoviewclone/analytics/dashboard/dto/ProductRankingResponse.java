package com.asoviewclone.analytics.dashboard.dto;

import java.util.List;

public record ProductRankingResponse(List<RankedProduct> products) {

  public record RankedProduct(
      String productId, long orderCount, long totalRevenueJpy, long popularityRank) {}
}
