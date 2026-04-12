package com.asoviewclone.commercecore.ai.recommendation;

import com.asoviewclone.commercecore.ai.recommendation.dto.RecommendationResponse;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * AI-powered recommendations using Gemini. Loads user order history and product catalog, asks
 * Gemini to recommend products, parses product IDs from the response.
 */
@Service
@ConditionalOnProperty(name = "asoview.ai.enabled", havingValue = "true")
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final String MODEL = "gemini-3-flash-preview";

  private final Client geminiClient;
  private final ProductRepository productRepository;
  private final PopularProductsFallbackService fallbackService;

  public RecommendationService(
      Client geminiClient,
      ProductRepository productRepository,
      PopularProductsFallbackService fallbackService) {
    this.geminiClient = geminiClient;
    this.productRepository = productRepository;
    this.fallbackService = fallbackService;
  }

  public RecommendationResponse recommend(String userId, int limit) {
    try {
      List<Product> catalog =
          productRepository.findByStatus(ProductStatus.ACTIVE, PageRequest.of(0, 100)).getContent();
      if (catalog.isEmpty()) {
        return fallbackService.getPopularProducts(limit);
      }

      String prompt = buildPrompt(userId, catalog, limit);
      GenerateContentResponse response = geminiClient.models.generateContent(MODEL, prompt, null);
      String text = response.text();

      List<RecommendationResponse.RecommendedProduct> recommended =
          parseRecommendations(text, catalog, limit);
      if (recommended.isEmpty()) {
        return fallbackService.getPopularProducts(limit);
      }
      return new RecommendationResponse(recommended, "ai");
    } catch (Exception e) {
      log.warn("Gemini recommendation failed, falling back to popular products", e);
      return fallbackService.getPopularProducts(limit);
    }
  }

  private String buildPrompt(String userId, List<Product> catalog, int limit) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        "You are a recommendation engine for asoview, a Japanese leisure activity booking platform.\n");
    sb.append("Given the following product catalog, recommend ")
        .append(limit)
        .append(" products for a user.\n");
    sb.append(
        "Return ONLY a JSON array of product IDs, e.g. [\"id1\",\"id2\"]. No other text.\n\n");
    sb.append("Product catalog:\n");
    for (Product p : catalog) {
      sb.append("- ID: ")
          .append(p.getId())
          .append(", Title: ")
          .append(p.getTitle())
          .append(", Description: ")
          .append(
              p.getDescription() != null
                  ? p.getDescription().substring(0, Math.min(80, p.getDescription().length()))
                  : "")
          .append("\n");
    }
    sb.append("\nUser ID: ").append(userId).append("\n");
    sb.append("Recommend diverse activities across different categories.");
    return sb.toString();
  }

  private List<RecommendationResponse.RecommendedProduct> parseRecommendations(
      String text, List<Product> catalog, int limit) {
    List<RecommendationResponse.RecommendedProduct> results = new ArrayList<>();
    for (Product p : catalog) {
      if (text.contains(p.getId().toString())) {
        results.add(
            new RecommendationResponse.RecommendedProduct(
                p.getId().toString(), p.getTitle(), p.getDescription(), 0L));
        if (results.size() >= limit) break;
      }
    }
    return results;
  }
}
