package com.asoviewclone.searchservice.indexer;

import com.asoviewclone.searchservice.query.model.ProductDoc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.opensearch.client.Request;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pulls a product row from commerce-core via the public {@code GET /v1/products/{id}} endpoint and
 * indexes it into the OpenSearch products index. Used by both the manual reindex endpoint and the
 * startup backfill job.
 */
@Service
public class IndexerService {

  private static final Logger log = LoggerFactory.getLogger(IndexerService.class);

  private final RestClient restClient;
  private final RestHighLevelClient openSearchClient;
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final String indexName;

  @org.springframework.beans.factory.annotation.Autowired
  public IndexerService(
      RestHighLevelClient openSearchClient,
      @Value("${commerce-core.base-url:${COMMERCE_CORE_BASE_URL:http://localhost:8080}}")
          String commerceCoreBaseUrl,
      @Value("${search.index-name:asoview-products-local}") String indexName) {
    this.openSearchClient = openSearchClient;
    this.indexName = indexName;
    this.restClient = RestClient.builder().baseUrl(commerceCoreBaseUrl).build();
  }

  // Test seam: package-private constructor that lets tests inject a pre-built RestClient
  // (e.g. wired to MockRestServiceServer).
  IndexerService(RestHighLevelClient openSearchClient, RestClient restClient, String indexName) {
    this.openSearchClient = openSearchClient;
    this.restClient = restClient;
    this.indexName = indexName;
  }

  public void reindex(String productId) {
    try {
      String body =
          restClient.get().uri("/v1/products/{id}", productId).retrieve().body(String.class);
      if (body == null || body.isBlank()) {
        log.warn("commerce-core returned empty body for product {}", productId);
        return;
      }
      JsonNode node = mapper.readTree(body);
      ProductDoc doc = toDoc(node);
      indexDoc(doc);
      log.info("Reindexed product {}", productId);
    } catch (Exception e) {
      log.warn("Failed to reindex product {}: {}", productId, e.getMessage());
      throw new RuntimeException("reindex failed: " + productId, e);
    }
  }

  private ProductDoc toDoc(JsonNode node) {
    String id = node.path("id").asText(null);
    String name = node.path("title").asText(null);
    String description = node.path("description").asText(null);
    String areaId = node.path("venueId").asText(null);
    String categoryId = node.path("categoryId").asText(null);
    String status = node.path("status").asText(null);
    Long minPrice = null;
    JsonNode variants = node.path("variants");
    if (variants.isArray()) {
      for (JsonNode v : variants) {
        if (v.has("priceAmount") && !v.path("priceAmount").isNull()) {
          long p = v.path("priceAmount").asLong(Long.MAX_VALUE);
          if (minPrice == null || p < minPrice) {
            minPrice = p;
          }
        }
      }
    }
    return new ProductDoc(
        id, name, description, areaId, categoryId, minPrice, status, Instant.now().toString(), 0L);
  }

  private void indexDoc(ProductDoc doc) throws Exception {
    Request req =
        new Request("PUT", "/" + indexName + "/_doc/" + doc.productId() + "?refresh=true");
    req.setJsonEntity(mapper.writeValueAsString(doc));
    openSearchClient.getLowLevelClient().performRequest(req);
  }

  /**
   * Partial update: set only the popularityScore field on an existing document.
   *
   * @return true if the update succeeded, false on failure (logged as error)
   */
  public boolean updatePopularityScore(String productId, long score) {
    try {
      String encodedId =
          java.net.URLEncoder.encode(productId, java.nio.charset.StandardCharsets.UTF_8);
      Request req = new Request("POST", "/" + indexName + "/_update/" + encodedId);
      req.setJsonEntity("{\"doc\":{\"popularityScore\":" + score + "}}");
      openSearchClient.getLowLevelClient().performRequest(req);
      return true;
    } catch (Exception e) {
      log.error("Failed to update popularityScore for {}: {}", productId, e.getMessage(), e);
      return false;
    }
  }

  public boolean markerExists() {
    try {
      Request req = new Request("GET", "/" + indexName + "/_doc/asoview-backfill-marker-v1");
      var resp = openSearchClient.getLowLevelClient().performRequest(req);
      String body =
          new String(resp.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
      return body.contains("\"found\":true");
    } catch (Exception e) {
      return false;
    }
  }

  public void writeMarker() {
    try {
      Request req =
          new Request("PUT", "/" + indexName + "/_doc/asoview-backfill-marker-v1?refresh=true");
      req.setJsonEntity("{\"productId\":\"asoview-backfill-marker-v1\",\"status\":\"MARKER\"}");
      openSearchClient.getLowLevelClient().performRequest(req);
    } catch (Exception e) {
      log.warn("Failed to write backfill marker: {}", e.getMessage());
    }
  }
}
