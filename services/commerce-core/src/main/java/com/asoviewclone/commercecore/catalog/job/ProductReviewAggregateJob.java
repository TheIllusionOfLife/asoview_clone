package com.asoviewclone.commercecore.catalog.job;

import com.asoviewclone.commercecore.catalog.repository.ProductReviewAggregateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic job that recomputes the product_review_aggregates table via a single SQL aggregate
 * statement (no N+1). Runs on a fixed delay so the catalog list/detail endpoints can batch-read
 * aggregates without joining to reviews.
 */
@Component
public class ProductReviewAggregateJob {

  private static final Logger log = LoggerFactory.getLogger(ProductReviewAggregateJob.class);

  private final ProductReviewAggregateRepository repository;

  public ProductReviewAggregateJob(ProductReviewAggregateRepository repository) {
    this.repository = repository;
  }

  @Scheduled(fixedDelayString = "${app.catalog.review-aggregate.recompute-ms:300000}")
  @Transactional
  public void recompute() {
    int rows = repository.recomputeAll();
    log.debug("product_review_aggregates recompute upserted {} rows", rows);
  }
}
