package com.asoviewclone.commercecore.catalog.event;

import com.asoviewclone.commercecore.events.PubSubPublisher;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
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
 * <p>Publish failures rethrow. Spring's {@code @TransactionalEventListener(AFTER_COMMIT)} handler
 * catches the exception and logs at ERROR — the JPA tx is already committed, so rethrowing surfaces
 * the delivery failure in ops dashboards without affecting catalog data. Recovery paths (startup
 * {@code IndexerBackfillJob}, per-product admin reindex, the next seed re-run) still catch up
 * eventually, but the ERROR line makes Pub/Sub health visible instead of silent. Per CLAUDE.md
 * "logging is not error handling" — at system boundaries, error paths throw.
 *
 * <p><b>Durability trade-off:</b> direct AFTER_COMMIT publish is not durable — a Pub/Sub outage
 * between JPA commit and the publish call drops the message. The existing outbox pattern (see
 * {@code com.asoviewclone.commercecore.events.OutboxRelayJob}) would give at-least-once semantics,
 * but this listener publishes directly for simplicity. The recovery floors above ({@code
 * IndexerBackfillJob} on startup, admin reindex endpoint for targeted repair) cover the common
 * case. Revisit if ops ever observes stale-index reports that the ERROR log can confirm but the
 * recovery floors miss.
 *
 * <p>The only {@code PubSubPublisher} implementation ({@code GcpPubSubPublisher}) is gated on a
 * non-empty {@code spring.cloud.gcp.project-id} via {@code @ConditionalOnExpression}, so unit /
 * test profiles without Pub/Sub wiring don't get one — hence the {@code Optional} injection here.
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
    // Fresh eventId per publish so log correlation reflects the update
    // attempt, not the product identity (which would collide across
    // successive updates of the same row).
    String eventId = UUID.randomUUID().toString();
    publisher.get().publish(TOPIC, eventId, productId.getBytes(StandardCharsets.UTF_8));
    log.info("Published product-index-events eventId={} productId={}", eventId, productId);
  }
}
