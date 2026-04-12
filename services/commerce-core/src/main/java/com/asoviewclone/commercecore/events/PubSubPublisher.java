package com.asoviewclone.commercecore.events;

/**
 * Abstraction over Pub/Sub publishing so the outbox relay can be tested without a real Pub/Sub
 * connection. Implementations must block until delivery is confirmed or throw on failure.
 */
public interface PubSubPublisher {

  /**
   * Publish a message to the given topic. Blocks until delivery is confirmed.
   *
   * @param topic the Pub/Sub topic name
   * @param eventId stable event id for consumer idempotency (set as a message attribute)
   * @param data the serialized proto bytes
   * @throws RuntimeException if publishing fails
   */
  void publish(String topic, String eventId, byte[] data);
}
