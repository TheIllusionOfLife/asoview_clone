package com.asoviewclone.commercecore.catalog.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Log-only listener for {@link ProductUpsertedEvent}. Bound to {@code AFTER_COMMIT} so a rolled
 * back JPA transaction does not produce a stale index update.
 *
 * <p>TODO: replace the log line with a Spring Cloud GCP Pub/Sub publish to a {@code
 * product-upserted} topic. The search-service indexer will subscribe and pull the canonical product
 * row from {@code GET /v1/products/{id}}. The decoupling is in place today so the follow-up PR only
 * needs to flip the wiring without touching the catalog domain.
 */
@Component
public class ProductIndexEventListener {

  private static final Logger log = LoggerFactory.getLogger(ProductIndexEventListener.class);

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProductUpserted(ProductUpsertedEvent event) {
    log.info("Product indexed: {}", event.productId());
  }
}
