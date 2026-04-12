package com.asoviewclone.commercecore.ai.recommendation;

import com.asoviewclone.commercecore.ai.recommendation.dto.RecommendationResponse;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/recommendations")
public class RecommendationController {

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
      @AuthenticationPrincipal UserDetails user, @RequestParam(defaultValue = "5") int limit) {
    if (recommendationService.isPresent()) {
      return recommendationService.get().recommend(user.getUsername(), limit);
    }
    return fallbackService.getPopularProducts(limit);
  }
}
