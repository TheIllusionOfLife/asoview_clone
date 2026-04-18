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
 * contract). On successful reindex: ack. On exception: nack so Pub/Sub's retry_policy re-delivers
 * with backoff. No DLQ routing — a failed reindex is recoverable via the startup {@link
 * IndexerBackfillJob} + per-product admin endpoint.
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
