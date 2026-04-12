package com.asoviewclone.commercecore.ai.recommendation;

import com.asoviewclone.commercecore.ai.recommendation.dto.RecommendationResponse;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Fallback recommendation source: returns the most recently created active products. Used when
 * Gemini is unavailable, times out, or the AI feature is disabled.
 */
@Service
public class PopularProductsFallbackService {

  private final ProductRepository productRepository;

  public PopularProductsFallbackService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  public RecommendationResponse getPopularProducts(int limit) {
    List<Product> products =
        productRepository
            .findByStatus(
                ProductStatus.ACTIVE,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent();
    List<RecommendationResponse.RecommendedProduct> recommended =
        products.stream()
            .map(
                p ->
                    new RecommendationResponse.RecommendedProduct(
                        p.getId().toString(), p.getTitle(), p.getDescription(), 0L))
            .toList();
    return new RecommendationResponse(recommended, "popular");
  }
}
