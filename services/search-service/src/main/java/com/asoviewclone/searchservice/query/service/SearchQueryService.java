package com.asoviewclone.searchservice.query.service;

import com.asoviewclone.searchservice.query.dto.AutosuggestResponse;
import com.asoviewclone.searchservice.query.dto.ProductSearchResponse;
import com.asoviewclone.searchservice.query.model.SearchHit;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds an OpenSearch query DSL using the low-level REST client (the high-level client is in
 * maintenance mode and the request builder API is unstable across the 2.x line). We hand-build the
 * JSON via Jackson so the test fixtures are easy to read.
 */
@Service
public class SearchQueryService {

  private static final Logger log = LoggerFactory.getLogger(SearchQueryService.class);

  private final RestHighLevelClient client;
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final String indexName;

  public SearchQueryService(
      RestHighLevelClient client,
      @Value("${search.index-name:asoview-products-local}") String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

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
    int from = safePage * safeSize;

    var root = mapper.createObjectNode();
    root.put("from", from);
    root.put("size", safeSize);

    var bool = mapper.createObjectNode();
    var must = mapper.createArrayNode();
    if (q != null && !q.isBlank()) {
      var multi = mapper.createObjectNode();
      var fields = mapper.createArrayNode();
      fields.add("name");
      fields.add("description");
      var ms = mapper.createObjectNode();
      ms.put("query", q);
      ms.set("fields", fields);
      multi.set("multi_match", ms);
      must.add(multi);
    } else {
      must.add(mapper.createObjectNode().set("match_all", mapper.createObjectNode()));
    }
    bool.set("must", must);

    var filter = mapper.createArrayNode();
    // Hard filter on status=ACTIVE so inactive/draft products that ended up in
    // the index (orphaned documents from a stale reindex, or rows that got
    // soft-deleted post-publication) are never publicly searchable.
    // (PR #21 Codex finding: search exposed inactive products.)
    filter.add(termFilter("status", "ACTIVE"));
    if (areaId != null && !areaId.isBlank()) {
      filter.add(termFilter("areaId", areaId));
    }
    if (categoryId != null && !categoryId.isBlank()) {
      filter.add(termFilter("categoryId", categoryId));
    }
    if (minPrice != null || maxPrice != null) {
      var range = mapper.createObjectNode();
      var price = mapper.createObjectNode();
      if (minPrice != null) {
        price.put("gte", minPrice);
      }
      if (maxPrice != null) {
        price.put("lte", maxPrice);
      }
      range.set("minPrice", price);
      filter.add(mapper.createObjectNode().set("range", range));
    }
    bool.set("filter", filter);

    root.set("query", mapper.createObjectNode().set("bool", bool));

    var sortNode = mapper.createArrayNode();
    switch (sort == null ? "" : sort) {
      case "price_asc" -> sortNode.add(mapper.createObjectNode().set("minPrice", orderNode("asc")));
      case "price_desc" ->
          sortNode.add(mapper.createObjectNode().set("minPrice", orderNode("desc")));
      case "name_asc" ->
          sortNode.add(mapper.createObjectNode().set("name.keyword", orderNode("asc")));
      default -> {
        // relevance default — no explicit sort
      }
    }
    if (!sortNode.isEmpty()) {
      root.set("sort", sortNode);
    }

    JsonNode response = executeSearch(root.toString());
    return parseSearchResponse(response, safePage, safeSize);
  }

  public AutosuggestResponse suggest(String q) {
    if (q == null || q.isBlank()) {
      return new AutosuggestResponse(List.of());
    }
    var root = mapper.createObjectNode();
    root.put("size", 5);
    // Same status=ACTIVE hard filter as the main search query.
    var bool = mapper.createObjectNode();
    var must = mapper.createArrayNode();
    var match = mapper.createObjectNode();
    var inner = mapper.createObjectNode();
    inner.put("query", q);
    match.set("name", inner);
    must.add(mapper.createObjectNode().set("match_phrase_prefix", match));
    bool.set("must", must);
    var sFilter = mapper.createArrayNode();
    sFilter.add(termFilter("status", "ACTIVE"));
    bool.set("filter", sFilter);
    root.set("query", mapper.createObjectNode().set("bool", bool));

    JsonNode response = executeSearch(root.toString());
    List<AutosuggestResponse.Suggestion> suggestions = new ArrayList<>();
    JsonNode hits = response.path("hits").path("hits");
    for (JsonNode hit : hits) {
      JsonNode src = hit.path("_source");
      suggestions.add(
          new AutosuggestResponse.Suggestion(
              src.path("productId").asText(null), src.path("name").asText(null)));
    }
    return new AutosuggestResponse(suggestions);
  }

  private JsonNode executeSearch(String body) {
    try {
      Request req = new Request("POST", "/" + indexName + "/_search");
      req.setJsonEntity(body);
      Response resp = client.getLowLevelClient().performRequest(req);
      String json =
          new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
      return mapper.readTree(json);
    } catch (Exception e) {
      log.warn("OpenSearch query failed: {}", e.getMessage());
      throw new RuntimeException("search query failed", e);
    }
  }

  private ProductSearchResponse parseSearchResponse(JsonNode response, int page, int size) {
    long total = response.path("hits").path("total").path("value").asLong(0);
    JsonNode hits = response.path("hits").path("hits");
    List<SearchHit> content = new ArrayList<>();
    for (JsonNode hit : hits) {
      JsonNode src = hit.path("_source");
      content.add(
          new SearchHit(
              src.path("productId").asText(null),
              src.path("name").asText(null),
              src.path("description").asText(null),
              src.has("minPrice") && !src.path("minPrice").isNull()
                  ? src.path("minPrice").asLong()
                  : null,
              src.path("areaId").asText(null),
              src.path("categoryId").asText(null)));
    }
    return new ProductSearchResponse(content, total, page, size);
  }

  private tools.jackson.databind.node.ObjectNode termFilter(String field, String value) {
    var term = mapper.createObjectNode();
    term.put(field, value);
    return mapper.createObjectNode().set("term", term);
  }

  private tools.jackson.databind.node.ObjectNode orderNode(String order) {
    var node = mapper.createObjectNode();
    node.put("order", order);
    return node;
  }
}
