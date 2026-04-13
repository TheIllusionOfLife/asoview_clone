package com.asoviewclone.commercecore.ai.recommendation;

import com.asoviewclone.commercecore.ai.recommendation.dto.RecommendationResponse;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/recommendations")
public class RecommendationController {

  private static final int MAX_LIMIT = 20;

  private final Optional<RecommendationService> recommendationService;
  private final PopularProductsFallbackService fallbackService;

  public RecommendationController(
      Optional<RecommendationService> recommendationService,
      PopularProductsFallbackService fallbackService) {
    this.recommendationService = recommendationService;
    this.fallbackService = fallbackService;
  }

  @GetMapping
  public RecommendationResponse getRecommendations(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "5") int limit) {
    int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
    if (recommendationService.isPresent()) {
      return recommendationService.get().recommend(user.firebaseUid(), safeLimit);
    }
    return fallbackService.getPopularProducts(safeLimit);
  }
}
