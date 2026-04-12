package com.asoviewclone.commercecore.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 100)
  private String eventType;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(nullable = false, length = 100)
  private String topic;

  @Column(nullable = false, columnDefinition = "BYTEA")
  private byte[] payload;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  protected OutboxEvent() {}

  public OutboxEvent(
      String eventId, String eventType, String aggregateId, String topic, byte[] payload) {
    this.id = UUID.randomUUID();
    this.eventId = eventId;
    this.eventType = eventType;
    this.aggregateId = aggregateId;
    this.topic = topic;
    this.payload = payload;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getAggregateId() {
    return aggregateId;
  }

  public String getTopic() {
    return topic;
  }

  public byte[] getPayload() {
    return payload;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getFailedAt() {
    return failedAt;
  }

  public void markPublished() {
    this.publishedAt = Instant.now();
  }
}
