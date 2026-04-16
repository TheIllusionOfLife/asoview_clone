package com.asoviewclone.searchservice.query.service;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;

/**
 * Provider-neutral query surface for the search-service. Implementations back this with OpenSearch
 * (legacy) or Vertex AI Search (Discovery Engine API). The gateway-facing REST contract in {@code
 * SearchController} must be identical regardless of provider.
 */
public interface SearchQueryPort {

  ProductSearchResponse search(
      String q,
      String areaId,
      String categoryId,
      Long minPrice,
      Long maxPrice,
      String sort,
      int page,
      int size);

  AutosuggestResponse suggest(String q);
}
