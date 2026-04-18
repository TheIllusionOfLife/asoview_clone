package com.asoviewclone.searchservice.query.service;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.model.SearchHit;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.discoveryengine.v1.SearchRequest;
import com.google.cloud.discoveryengine.v1.SearchResponse;
import com.google.cloud.discoveryengine.v1.SearchServiceClient;
import com.google.protobuf.Struct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Vertex AI Search implementation of {@link SearchQueryPort}. Uses the Discovery Engine API's
 * structured-data store pattern: query against a serving config, compose a filter expression over
 * indexable fields, and apply a piecewise {@code ConditionBoostSpec} over {@code popularityScore}
 * on the relevance sort path.
 *
 * <p>{@code suggest(...)} is intentionally implemented as a top-5 name-biased search rather than
 * {@code CompletionServiceClient.completeQuery(...)} because {@code CompleteQuery} returns query
 * strings, not product documents — the REST contract returns {@code (productId, name)} pairs, so a
 * document-level search is the right primitive. This is the permanent implementation, not a
 * fallback for a cold data store.
 */
@Service
public class VertexAiSearchQueryService implements SearchQueryPort {

  private static final Logger log = LoggerFactory.getLogger(VertexAiSearchQueryService.class);

  private final SearchServiceClient searchClient;
  private final String servingConfig;

  public VertexAiSearchQueryService(
      SearchServiceClient searchClient,
      @Value("${vertex.project-id}") String projectId,
      @Value("${vertex.location:global}") String location,
      @Value("${vertex.collection:default_collection}") String collection,
      @Value("${vertex.engine-id}") String engineId,
      @Value("${vertex.serving-config:default_search}") String servingConfigId) {
    this.searchClient = searchClient;
    this.servingConfig =
        String.format(
            "projects/%s/locations/%s/collections/%s/engines/%s/servingConfigs/%s",
            projectId, location, collection, engineId, servingConfigId);
  }

  @Override
  public ProductSearchResponse search(
      String q,
      String areaId,
      String categoryId,
      Long minPrice,
      Long maxPrice,
      String sort,
      int page,
      int size) {
    int safeSize = Math.max(1, Math.min(size, 100));
    int safePage = Math.max(0, page);
    SearchRequest request =
        buildSearchRequest(q, areaId, categoryId, minPrice, maxPrice, sort, safePage, safeSize);
    SearchResponse response;
    try {
      response = executeSearch(request);
    } catch (RuntimeException e) {
      // Graceful fallback for sort-config drift: if Vertex rejects the
      // orderBy (key-property mapping not yet applied, tier limitation, or
      // a schema regression) retry once without orderBy + popularity boost
      // so the user sees relevance-ordered results instead of an HTTP 500.
      // The caller still paginates, so the UX impact is just "sort ignored".
      if (sort != null && !sort.isBlank() && isInvalidOrderBy(e)) {
        log.warn(
            "orderBy '{}' rejected by Vertex; falling back to relevance sort for this query", sort);
        SearchRequest fallback =
            buildSearchRequest(q, areaId, categoryId, minPrice, maxPrice, null, safePage, safeSize);
        response = executeSearch(fallback);
      } else {
        throw e;
      }
    }
    return parseSearchResponse(response, safePage, safeSize);
  }

  private static boolean isInvalidOrderBy(Throwable t) {
    Throwable cur = t;
    while (cur != null) {
      if (cur instanceof ApiException api
          && api.getStatusCode() != null
          && "INVALID_ARGUMENT".equals(api.getStatusCode().getCode().name())) {
        String msg = api.getMessage() == null ? "" : api.getMessage();
        return msg.contains("orderBy") || msg.contains("order_by");
      }
      cur = cur.getCause();
    }
    return false;
  }

  /** Package-private for testing the filter/sort/boost-spec composition. */
  SearchRequest buildSearchRequest(
      String q,
      String areaId,
      String categoryId,
      Long minPrice,
      Long maxPrice,
      String sort,
      int safePage,
      int safeSize) {
    SearchRequest.Builder builder =
        SearchRequest.newBuilder()
            .setServingConfig(servingConfig)
            .setQuery(q == null ? "" : q)
            .setPageSize(safeSize)
            .setOffset(safePage * safeSize)
            .setFilter(buildFilter(areaId, categoryId, minPrice, maxPrice));

    boolean explicitSort = applySort(builder, sort);
    if (!explicitSort) {
      builder.setBoostSpec(popularityBoostSpec());
    }
    return builder.build();
  }

  @Override
  public AutosuggestResponse suggest(String q) {
    if (q == null || q.isBlank()) {
      return new AutosuggestResponse(List.of());
    }
    SearchRequest request =
        SearchRequest.newBuilder()
            .setServingConfig(servingConfig)
            .setQuery(q)
            .setPageSize(5)
            .setFilter("status: ANY(\"ACTIVE\")")
            .build();
    SearchResponse response = executeSearch(request);
    List<AutosuggestResponse.Suggestion> suggestions = new ArrayList<>();
    for (SearchResponse.SearchResult result : response.getResultsList()) {
      Struct data = result.getDocument().getStructData();
      String productId = stringField(data, "productId");
      if (productId == null) {
        // Schema drift or a non-product sentinel (e.g. backfill marker) would otherwise
        // leak a null-id suggestion to clients; skip instead.
        continue;
      }
      suggestions.add(new AutosuggestResponse.Suggestion(productId, stringField(data, "name")));
    }
    return new AutosuggestResponse(suggestions);
  }

  private String buildFilter(String areaId, String categoryId, Long minPrice, Long maxPrice) {
    // Discovery Engine filter syntax for indexable string fields uses `:` with ANY().
    // `=` yields "Unsupported field X on comparison operators" from the server for
    // non-numeric fields. Verified against the v1 REST endpoint with 50 indexed docs.
    StringBuilder f = new StringBuilder("status: ANY(\"ACTIVE\")");
    if (areaId != null && !areaId.isBlank()) {
      f.append(" AND areaId: ANY(\"").append(escape(areaId)).append("\")");
    }
    if (categoryId != null && !categoryId.isBlank()) {
      f.append(" AND categoryId: ANY(\"").append(escape(categoryId)).append("\")");
    }
    if (minPrice != null) {
      f.append(" AND minPrice >= ").append(minPrice);
    }
    if (maxPrice != null) {
      f.append(" AND minPrice <= ").append(maxPrice);
    }
    return f.toString();
  }

  private boolean applySort(SearchRequest.Builder builder, String sort) {
    if (sort == null) {
      return false;
    }
    // Discovery Engine generic vertical accepts orderBy only on predefined
    // key properties (price, rating, etc.) or on fields tagged with a
    // keyPropertyMapping. `minPrice` carries `keyPropertyMapping: price` in
    // products-schema.json — so the orderBy path is the bare key property
    // name `price`, not `minPrice` or `structData.minPrice` (both produce
    // `Unsupported field in orderBy`). `name` is not a key property so
    // sorting by name is not supported on this vertical.
    switch (sort) {
      case "price_asc" -> builder.setOrderBy("price asc");
      case "price_desc" -> builder.setOrderBy("price desc");
      default -> {
        return false;
      }
    }
    return true;
  }

  /**
   * Piecewise popularity boost. Discovery Engine Standard tier supports condition-based boost
   * specs; cut points below approximate a log1p curve over {@code order_count}.
   */
  private SearchRequest.BoostSpec popularityBoostSpec() {
    return SearchRequest.BoostSpec.newBuilder()
        .addConditionBoostSpecs(
            SearchRequest.BoostSpec.ConditionBoostSpec.newBuilder()
                .setCondition("popularityScore >= 1 AND popularityScore < 5")
                .setBoost(0.10f)
                .build())
        .addConditionBoostSpecs(
            SearchRequest.BoostSpec.ConditionBoostSpec.newBuilder()
                .setCondition("popularityScore >= 5 AND popularityScore < 20")
                .setBoost(0.25f)
                .build())
        .addConditionBoostSpecs(
            SearchRequest.BoostSpec.ConditionBoostSpec.newBuilder()
                .setCondition("popularityScore >= 20 AND popularityScore < 50")
                .setBoost(0.40f)
                .build())
        .addConditionBoostSpecs(
            SearchRequest.BoostSpec.ConditionBoostSpec.newBuilder()
                .setCondition("popularityScore >= 50 AND popularityScore < 200")
                .setBoost(0.60f)
                .build())
        .addConditionBoostSpecs(
            SearchRequest.BoostSpec.ConditionBoostSpec.newBuilder()
                .setCondition("popularityScore >= 200")
                .setBoost(0.80f)
                .build())
        .build();
  }

  private SearchResponse executeSearch(SearchRequest request) {
    try {
      return searchClient.search(request).getPage().getResponse();
    } catch (ApiException e) {
      log.warn(
          "Vertex AI Search query failed: status={}, message={}",
          e.getStatusCode().getCode(),
          e.getMessage());
      throw new RuntimeException("vertex search query failed", e);
    } catch (Exception e) {
      log.warn("Vertex AI Search query failed: {}", e.getMessage());
      throw new RuntimeException("vertex search query failed", e);
    }
  }

  private ProductSearchResponse parseSearchResponse(SearchResponse response, int page, int size) {
    List<SearchHit> content = new ArrayList<>();
    for (SearchResponse.SearchResult result : response.getResultsList()) {
      Struct data = result.getDocument().getStructData();
      content.add(
          new SearchHit(
              stringField(data, "productId"),
              stringField(data, "name"),
              stringField(data, "description"),
              longField(data, "minPrice"),
              stringField(data, "areaId"),
              stringField(data, "categoryId")));
    }
    return new ProductSearchResponse(content, response.getTotalSize(), page, size);
  }

  private static String stringField(Struct data, String field) {
    com.google.protobuf.Value v = data.getFieldsOrDefault(field, null);
    if (v == null || v.getKindCase() != com.google.protobuf.Value.KindCase.STRING_VALUE) {
      return null;
    }
    String s = v.getStringValue();
    return s.isEmpty() ? null : s;
  }

  private static Long longField(Struct data, String field) {
    com.google.protobuf.Value v = data.getFieldsOrDefault(field, null);
    if (v == null || v.getKindCase() != com.google.protobuf.Value.KindCase.NUMBER_VALUE) {
      return null;
    }
    return (long) v.getNumberValue();
  }

  /** Escape backslashes and double quotes in filter-expression string literals. */
  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
