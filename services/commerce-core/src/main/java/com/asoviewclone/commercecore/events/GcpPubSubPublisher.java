package com.asoviewclone.commercecore.events;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Pub/Sub publisher backed by Spring Cloud GCP PubSubTemplate. */
@Component
public class GcpPubSubPublisher implements PubSubPublisher {

  private static final Logger log = LoggerFactory.getLogger(GcpPubSubPublisher.class);

  private final PubSubTemplate pubSubTemplate;

  public GcpPubSubPublisher(PubSubTemplate pubSubTemplate) {
    this.pubSubTemplate = pubSubTemplate;
  }

  @Override
  public void publish(String topic, String eventId, byte[] data) {
    pubSubTemplate
        .publish(topic, data, Map.of("event_id", eventId))
        .whenComplete(
            (messageId, ex) -> {
              if (ex != null) {
                log.error("Failed to publish to topic={} eventId={}", topic, eventId, ex);
              } else {
                log.debug(
                    "Published to topic={} eventId={} messageId={}", topic, eventId, messageId);
              }
            });
  }
}
