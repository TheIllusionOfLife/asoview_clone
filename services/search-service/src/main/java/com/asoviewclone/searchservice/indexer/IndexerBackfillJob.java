package com.asoviewclone.searchservice.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One-shot backfill on first startup. Paginates through {@code GET
 * /v1/products?status=ACTIVE&page=N&size=PAGE_SIZE} from commerce-core and reindexes every
 * document. The backfill marker is only written after a fully-paginated, zero-failure pass —
 * partial runs leave the marker absent so the next pod restart retries from the top. This closes
 * the Codex-flagged idempotency gap where the legacy single-page implementation could stamp "done"
 * on a half-failed run.
 */
@Component
@Order(100) // run after index template / schema bootstraps (@Order(50))
public class IndexerBackfillJob implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(IndexerBackfillJob.class);
  private static final int PAGE_SIZE = 500;

  private final IndexerPort indexerService;
  private final RestClient restClient;
  private final ObjectMapper mapper = JsonMapper.builder().build();
  private final boolean enabled;

  @Autowired
  public IndexerBackfillJob(
      IndexerPort indexerService,
      @Value("${commerce-core.base-url:${COMMERCE_CORE_BASE_URL:http://localhost:8080}}")
          String commerceCoreBaseUrl,
      @Value("${search.backfill.enabled:true}") boolean enabled) {
    this.indexerService = indexerService;
    this.restClient = RestClient.builder().baseUrl(commerceCoreBaseUrl).build();
    this.enabled = enabled;
  }

  /** Test seam: inject a pre-built RestClient so tests can bind it to a mock HTTP stub. */
  IndexerBackfillJob(IndexerPort indexerService, RestClient restClient, boolean enabled) {
    this.indexerService = indexerService;
    this.restClient = restClient;
    this.enabled = enabled;
  }

  /** Test seam: subclasses can override to bypass HTTP entirely for pagination-logic tests. */
  protected String fetchPage(int page, int size) {
    return restClient
        .get()
        .uri("/v1/products?status=ACTIVE&page={page}&size={size}", page, size)
        .retrieve()
        .body(String.class);
  }

  @Override
  public void run(String... args) {
    if (!enabled) {
      log.info("IndexerBackfillJob disabled via search.backfill.enabled=false");
      return;
    }
    try {
      if (indexerService.isBackfillComplete()) {
        log.info("Backfill marker present, skipping initial backfill");
        return;
      }

      int page = 0;
      int indexed = 0;
      int failed = 0;
      boolean sawFinalPage = false;
      while (!sawFinalPage) {
        String body = fetchPage(page, PAGE_SIZE);
        if (body == null || body.isBlank()) {
          log.warn("Backfill: commerce-core returned empty response for page {}", page);
          return;
        }
        JsonNode root = mapper.readTree(body);
        JsonNode content = root.path("content");
        int pageCount = 0;
        for (JsonNode product : content) {
          pageCount++;
          String id = product.path("id").asText(null);
          if (id == null) {
            // Treat a missing id as a failure so a malformed feed doesn't quietly let us
            // stamp the "done" marker while skipping real documents.
            failed++;
            log.warn("Backfill: product entry is missing id on page {}", page);
            continue;
          }
          try {
            indexerService.reindex(id);
            indexed++;
          } catch (Exception inner) {
            failed++;
            log.warn("Backfill: failed for product {}: {}", id, inner.getMessage());
          }
        }
        if (pageCount < PAGE_SIZE) {
          sawFinalPage = true;
        }
        page++;
      }

      if (failed == 0) {
        try {
          indexerService.markBackfillComplete();
          log.info("Backfill complete: indexed {} products across {} pages", indexed, page);
        } catch (Exception markerEx) {
          log.warn(
              "Backfill indexed {} products across {} pages but marker write failed: {}. "
                  + "Will retry on next boot.",
              indexed,
              page,
              markerEx.getMessage());
        }
      } else {
        log.warn(
            "Backfill indexed {} products but {} failed; marker NOT written (retry on next boot)",
            indexed,
            failed);
      }
    } catch (Exception e) {
      log.warn("Backfill aborted: {}. search-service will continue to start.", e.getMessage());
    }
  }
}
