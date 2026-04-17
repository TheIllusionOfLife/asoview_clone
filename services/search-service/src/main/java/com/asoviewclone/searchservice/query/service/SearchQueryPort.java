package com.asoviewclone.searchservice.query.service;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;

/**
 * Query surface for the search-service, backed by Vertex AI Search (Discovery Engine API). The
 * gateway-facing REST contract in {@code SearchController} is the stable external API; this
 * interface keeps the controller decoupled from client SDK types.
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
