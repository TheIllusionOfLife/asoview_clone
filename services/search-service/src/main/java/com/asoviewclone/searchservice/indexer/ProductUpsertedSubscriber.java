package com.asoviewclone.searchservice.indexer;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pulls {@code product-index-events} messages published by commerce-core's {@link
 * com.asoviewclone.commercecore.catalog.event.ProductIndexEventListener} after an AFTER_COMMIT
 * catalog write and drives a reindex against Vertex AI Search.
 *
 * <p>The message body is the raw productId UTF-8 string (matches commerce-core's publisher
 * contract). Outcomes:
 *
 * <ul>
 *   <li>empty / blank payload: ack + log.error. Treat as a poison-pill we won't retry — replaying
 *       forever on a malformed publisher bug just inflates logs.
 *   <li>reindex succeeds: ack + log.info.
 *   <li>reindex fails: nack so Pub/Sub's retry_policy re-delivers with backoff. Pub/Sub's built-in
 *       delivery_attempt count caps retries; after N attempts the message either flows to a DLQ
 *       (when configured) or is acked by Pub/Sub's retention policy (7d default). Recovery paths
 *       (startup {@link IndexerBackfillJob}, per-product admin endpoint, next seed re-run) cover
 *       the worst case.
 * </ul>
 *
 * <p>Gated on {@code search.pubsub.subscriber.enabled=true} so local / test profiles without real
 * Pub/Sub wiring don't fail to start. Defaults to true; the dev overlay keeps the default, tests
 * override via property.
 */
@Component
@ConditionalOnBean(PubSubTemplate.class)
@ConditionalOnProperty(
    name = "search.pubsub.subscriber.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProductUpsertedSubscriber {

  private static final Logger log = LoggerFactory.getLogger(ProductUpsertedSubscriber.class);

  private final PubSubTemplate pubSubTemplate;
  private final IndexerPort indexerService;
  private final String subscriptionName;

  @Autowired
  public ProductUpsertedSubscriber(
      PubSubTemplate pubSubTemplate,
      IndexerPort indexerService,
      @Value("${search.pubsub.subscription:search-service-product-index-sub}")
          String subscriptionName) {
    this.pubSubTemplate = pubSubTemplate;
    this.indexerService = indexerService;
    this.subscriptionName = subscriptionName;
  }

  @PostConstruct
  public void start() {
    log.info("Subscribing to Pub/Sub subscription={}", subscriptionName);
    pubSubTemplate.subscribeAndConvert(subscriptionName, this::handle, String.class);
  }

  void handle(ConvertedBasicAcknowledgeablePubsubMessage<String> message) {
    String productId = message.getPayload();
    if (productId == null || productId.isBlank()) {
      // Poison pill: publisher bug or malformed message. Ack and log loudly;
      // infinite retry just inflates logs without ever succeeding.
      log.error("Discarding product-index-events message with empty productId payload");
      message.ack();
      return;
    }
    try {
      indexerService.reindex(productId);
      message.ack();
      log.info("Reindexed productId={} from product-index-events", productId);
    } catch (RuntimeException ex) {
      log.warn(
          "Reindex failed for productId={}; nacking for Pub/Sub retry: {}",
          productId,
          ex.getMessage());
      message.nack();
    }
  }
}
