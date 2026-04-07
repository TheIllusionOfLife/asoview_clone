package com.asoviewclone.searchservice.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One-shot backfill on first startup. Walks {@code GET /v1/products?status=ACTIVE&size=1000} from
 * commerce-core and reindexes everything. Idempotent: a marker doc in the index causes subsequent
 * runs to be no-ops. Failures are swallowed so search-service still starts even if commerce-core is
 * unreachable.
 */
@Component
@Order(100) // run after IndexTemplateBootstrap
public class IndexerBackfillJob implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(IndexerBackfillJob.class);

  private final IndexerService indexerService;
  private final RestClient restClient;
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final boolean enabled;

  public IndexerBackfillJob(
      IndexerService indexerService,
      @Value("${commerce-core.base-url:${COMMERCE_CORE_BASE_URL:http://localhost:8080}}")
          String commerceCoreBaseUrl,
      @Value("${search.backfill.enabled:true}") boolean enabled) {
    this.indexerService = indexerService;
    this.restClient = RestClient.builder().baseUrl(commerceCoreBaseUrl).build();
    this.enabled = enabled;
  }

  @Override
  public void run(String... args) {
    if (!enabled) {
      log.info("IndexerBackfillJob disabled via search.backfill.enabled=false");
      return;
    }
    try {
      if (indexerService.markerExists()) {
        log.info("Backfill marker present, skipping initial backfill");
        return;
      }
      String body =
          restClient
              .get()
              .uri("/v1/products?status=ACTIVE&size=1000")
              .retrieve()
              .body(String.class);
      if (body == null || body.isBlank()) {
        log.warn("Backfill: commerce-core returned empty page");
        return;
      }
      JsonNode root = mapper.readTree(body);
      JsonNode content = root.path("content");
      int count = 0;
      for (JsonNode product : content) {
        String id = product.path("id").asText(null);
        if (id == null) {
          continue;
        }
        try {
          indexerService.reindex(id);
          count++;
        } catch (Exception inner) {
          log.warn("Backfill: failed for product {}: {}", id, inner.getMessage());
        }
      }
      indexerService.writeMarker();
      log.info("Backfill complete: indexed {} products", count);
    } catch (Exception e) {
      log.warn("Backfill aborted: {}. search-service will continue to start.", e.getMessage());
    }
  }
}
