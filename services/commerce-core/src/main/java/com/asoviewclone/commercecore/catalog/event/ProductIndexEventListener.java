package com.asoviewclone.commercecore.catalog.event;

import com.asoviewclone.commercecore.events.PubSubPublisher;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener for {@link ProductUpsertedEvent}. Bound to {@code AFTER_COMMIT} so a rolled-back JPA
 * transaction does not produce a stale index update.
 *
 * <p>Publishes the productId to the {@code product-index-events} Pub/Sub topic. search-service's
 * {@code ProductUpsertedSubscriber} picks it up and calls {@code VertexAiSearchIndexerService
 * .reindex(productId)} which re-pulls the canonical product row from {@code GET /v1/products/{id}}.
 * The decoupling lets seed updates flow end-to-end without the manual runbook in {@code
 * docs/operations/post-seed-vertex-reindex.md}.
 *
 * <p>Publish failures are logged but not rethrown: this path is best-effort because the startup
 * {@code IndexerBackfillJob} and the per-product admin endpoint provide recovery paths. A transient
 * Pub/Sub blip doesn't deserve a retry storm on the catalog write.
 *
 * <p>{@code PubSubPublisher} is {@code @ConditionalOnBean(PubSubTemplate.class)} so unit / test
 * profiles without Pub/Sub wiring still load this bean — hence the {@code Optional} injection.
 */
@Component
public class ProductIndexEventListener {

  static final String TOPIC = "product-index-events";
  private static final Logger log = LoggerFactory.getLogger(ProductIndexEventListener.class);

  private final Optional<PubSubPublisher> publisher;

  public ProductIndexEventListener(Optional<PubSubPublisher> publisher) {
    this.publisher = publisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProductUpserted(ProductUpsertedEvent event) {
    String productId = event.productId().toString();
    if (publisher.isEmpty()) {
      log.info("Product indexed (publisher disabled, skipping Pub/Sub): {}", productId);
      return;
    }
    try {
      publisher.get().publish(TOPIC, productId, productId.getBytes(StandardCharsets.UTF_8));
      log.info("Published product-index-events for productId={}", productId);
    } catch (RuntimeException ex) {
      // Recovery paths: IndexerBackfillJob on search-service startup + the
      // per-product admin endpoint + the next seed re-run re-publish. Not
      // rethrowing keeps catalog writes resilient to Pub/Sub flakiness.
      log.warn(
          "Failed to publish product-index-events for productId={}; reindex will catch up via"
              + " IndexerBackfillJob or admin reindex: {}",
          productId,
          ex.getMessage());
    }
  }
}
