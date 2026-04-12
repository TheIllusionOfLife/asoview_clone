package com.asoviewclone.commercecore.ai.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.ai.recommendation.dto.RecommendationResponse;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.google.genai.Client;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class RecommendationServiceTest {

  private Client geminiClient;
  private ProductRepository productRepository;
  private PopularProductsFallbackService fallbackService;
  private RecommendationService service;

  @BeforeEach
  void setUp() {
    geminiClient = mock(Client.class);
    productRepository = mock(ProductRepository.class);
    fallbackService = mock(PopularProductsFallbackService.class);
    service = new RecommendationService(geminiClient, productRepository, fallbackService);
  }

  @Test
  void recommend_fallsBackWhenGeminiFails() {
    when(productRepository.findByStatus(any(ProductStatus.class), any(Pageable.class)))
        .thenThrow(new RuntimeException("DB error"));
    RecommendationResponse fallback = new RecommendationResponse(List.of(), "popular");
    when(fallbackService.getPopularProducts(5)).thenReturn(fallback);

    RecommendationResponse result = service.recommend("user-1", 5);

    assertThat(result.source()).isEqualTo("popular");
  }

  @Test
  void recommend_fallsBackWhenCatalogEmpty() {
    Page<Product> emptyPage = new PageImpl<>(List.of());
    when(productRepository.findByStatus(any(ProductStatus.class), any(Pageable.class)))
        .thenReturn(emptyPage);
    RecommendationResponse fallback = new RecommendationResponse(List.of(), "popular");
    when(fallbackService.getPopularProducts(5)).thenReturn(fallback);

    RecommendationResponse result = service.recommend("user-1", 5);

    assertThat(result.source()).isEqualTo("popular");
  }
}
