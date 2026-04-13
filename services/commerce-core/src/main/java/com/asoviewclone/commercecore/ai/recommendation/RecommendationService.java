package com.asoviewclone.commercecore.ai.recommendation;

import com.asoviewclone.commercecore.ai.recommendation.dto.RecommendationResponse;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI-powered recommendations using Gemini. Loads product catalog with variants in a short read-only
 * transaction via TransactionTemplate (not @Transactional, because self-call from recommend() would
 * bypass the proxy). The Gemini API call runs outside the transaction to avoid holding a DB
 * connection during the external call.
 */
@Service
@ConditionalOnProperty(name = "asoview.ai.enabled", havingValue = "true")
public class RecommendationService {

  private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Client geminiClient;
  private final ProductRepository productRepository;
  private final PopularProductsFallbackService fallbackService;
  private final String model;
  private final TransactionTemplate readOnlyTx;

  public RecommendationService(
      Client geminiClient,
      ProductRepository productRepository,
      PopularProductsFallbackService fallbackService,
      PlatformTransactionManager txManager,
      @Value("${asoview.ai.model:gemini-3-flash-preview}") String model) {
    this.geminiClient = geminiClient;
    this.productRepository = productRepository;
    this.fallbackService = fallbackService;
    this.model = model;
    this.readOnlyTx = new TransactionTemplate(txManager);
    this.readOnlyTx.setReadOnly(true);
  }

  public RecommendationResponse recommend(String userId, int limit) {
    try {
      // Load products + force-init variants in a short read-only transaction,
      // then release the DB connection before calling the Gemini API.
      List<Product> catalog =
          readOnlyTx.execute(
              status -> {
                List<Product> products =
                    productRepository
                        .findByStatus(ProductStatus.ACTIVE, PageRequest.of(0, 100))
                        .getContent();
                for (Product p : products) {
                  p.getVariants().size(); // force-init lazy collection
                }
                return products;
              });
      if (catalog == null || catalog.isEmpty()) {
        return fallbackService.getPopularProducts(limit);
      }

      String prompt = buildPrompt(userId, catalog, limit);
      GenerateContentResponse response = geminiClient.models.generateContent(model, prompt, null);
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
    Map<String, Product> catalogMap =
        catalog.stream().collect(Collectors.toMap(p -> p.getId().toString(), Function.identity()));

    // Extract JSON array from response (may contain markdown fences)
    String jsonText = text.strip();
    int start = jsonText.indexOf('[');
    int end = jsonText.lastIndexOf(']');
    if (start < 0 || end < 0 || end <= start) {
      log.warn("Gemini response does not contain a JSON array: {}", text);
      return List.of();
    }
    jsonText = jsonText.substring(start, end + 1);

    try {
      List<String> ids = MAPPER.readValue(jsonText, new TypeReference<List<String>>() {});
      LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(ids);
      List<RecommendationResponse.RecommendedProduct> results = new ArrayList<>();
      for (String id : uniqueIds) {
        Product p = catalogMap.get(id);
        if (p != null) {
          results.add(
              new RecommendationResponse.RecommendedProduct(
                  p.getId().toString(), p.getTitle(), p.getDescription(), minPriceJpy(p)));
          if (results.size() >= limit) break;
        }
      }
      return results;
    } catch (Exception e) {
      log.warn("Failed to parse Gemini recommendation response as JSON", e);
      return List.of();
    }
  }

  private static long minPriceJpy(Product p) {
    return p.getVariants().stream()
        .map(v -> v.getPriceAmount())
        .filter(a -> a != null)
        .map(a -> a.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact())
        .min(Long::compareTo)
        .orElse(0L);
  }
}
