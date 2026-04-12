package com.asoviewclone.commercecore.events;

/**
 * Abstraction over Pub/Sub publishing so the outbox relay can be tested without a real Pub/Sub
 * connection.
 */
public interface PubSubPublisher {

  /**
   * Publish a message to the given topic.
   *
   * @param topic the Pub/Sub topic name
   * @param eventId stable event id for consumer idempotency (set as a message attribute)
   * @param data the serialized proto bytes
   */
  void publish(String topic, String eventId, byte[] data);
}
