package com.asoviewclone.commercecore.events;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pub/Sub publisher backed by Spring Cloud GCP PubSubTemplate. Blocks until delivery is confirmed
 * so the outbox relay can safely mark the row as published only after success.
 *
 * <p>Gated on {@code spring.cloud.gcp.project-id} (the same property {@code PubSubConfig} uses to
 * create {@code PubSubTemplate}) rather than {@code @ConditionalOnBean(PubSubTemplate.class)}.
 * {@code @ConditionalOnBean} on a {@code @Component} is evaluated before all {@code @Configuration}
 * classes are processed, so it returned false even when {@code PubSubTemplate} was registered —
 * which is exactly what happened in live deploy after PR #75/#76 (outbox publisher and {@code
 * ProductIndexEventListener} both silently took the {@code publisher.isEmpty()} path).
 * Property-gating sidesteps the ordering gotcha while matching the same activation condition.
 */
@Component
@ConditionalOnProperty("spring.cloud.gcp.project-id")
public class GcpPubSubPublisher implements PubSubPublisher {

  private static final Logger log = LoggerFactory.getLogger(GcpPubSubPublisher.class);
  private static final long PUBLISH_TIMEOUT_SECONDS = 30;

  private final PubSubTemplate pubSubTemplate;

  public GcpPubSubPublisher(PubSubTemplate pubSubTemplate) {
    this.pubSubTemplate = pubSubTemplate;
  }

  @Override
  public void publish(String topic, String eventId, byte[] data) {
    try {
      CompletableFuture<String> future =
          pubSubTemplate.publish(topic, data, Map.of("event_id", eventId));
      String messageId = future.get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      log.debug("Published to topic={} eventId={} messageId={}", topic, eventId, messageId);
    } catch (Exception ex) {
      throw new RuntimeException(
          "Failed to publish to topic=%s eventId=%s".formatted(topic, eventId), ex);
    }
  }
}
