package com.asoviewclone.searchservice.indexer;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic job that reads product popularity rankings from BigQuery and updates the popularityScore
 * field in OpenSearch documents. Higher-ranked products get higher scores so the function_score
 * query in SearchQueryService boosts them in relevance sorting.
 */
@Component
@ConditionalOnProperty(name = "search.popularity-sync.enabled", havingValue = "true")
public class PopularityScoreSyncJob {

  private static final Logger log = LoggerFactory.getLogger(PopularityScoreSyncJob.class);

  private static final String QUERY =
      "SELECT product_id, order_count FROM `%s.analytics_mart.product_ranking`";

  private final BigQuery bigQuery;
  private final IndexerService indexerService;
  private final String projectId;

  public PopularityScoreSyncJob(
      BigQuery bigQuery,
      IndexerService indexerService,
      @org.springframework.beans.factory.annotation.Value(
              "${search.popularity-sync.project-id:asoview-clone-dev}")
          String projectId) {
    this.bigQuery = bigQuery;
    this.indexerService = indexerService;
    this.projectId = projectId;
  }

  @Scheduled(
      fixedDelayString = "${search.popularity-sync.interval-ms:3600000}",
      initialDelayString = "${search.popularity-sync.initial-delay-ms:30000}")
  public void sync() {
    log.info("Starting popularityScore sync from BigQuery");
    try {
      String sql = String.format(QUERY, projectId);
      QueryJobConfiguration config =
          QueryJobConfiguration.newBuilder(sql).setUseLegacySql(false).build();
      TableResult result = bigQuery.query(config);
      int success = 0;
      int failed = 0;
      for (var row : result.iterateAll()) {
        try {
          if (row.get("product_id").isNull()) {
            continue;
          }
          String productId = row.get("product_id").getStringValue();
          long orderCount = row.get("order_count").getLongValue();
          if (indexerService.updatePopularityScore(productId, orderCount)) {
            success++;
          } else {
            failed++;
          }
        } catch (Exception rowEx) {
          failed++;
          log.error("PopularityScore sync: failed to process row: {}", rowEx.getMessage(), rowEx);
        }
      }
      log.info("PopularityScore sync complete: {} succeeded, {} failed", success, failed);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("PopularityScore sync interrupted: {}", e.getMessage(), e);
    } catch (Exception e) {
      log.error("PopularityScore sync failed: {}", e.getMessage(), e);
    }
  }
}
