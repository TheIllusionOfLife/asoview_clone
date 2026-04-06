package com.asoviewclone.commercecore.payments.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Replay-protection row. Inserted on first sight of a provider webhook event id; the PK unique
 * violation on a second attempt is our idempotency signal and is caught by the controller.
 *
 * <p>Keyed on {@code (provider, event_id)} since Stripe and PayPay can mint identical event id
 * strings — see V9 migration. The {@code @IdClass} carries both fields.
 */
@Entity
@Table(name = "processed_webhook_events")
@IdClass(ProcessedWebhookEventId.class)
public class ProcessedWebhookEvent {

  @Id
  @Column(nullable = false)
  private String provider;

  @Id
  @Column(name = "event_id")
  private String eventId;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected ProcessedWebhookEvent() {}

  public ProcessedWebhookEvent(String eventId, String provider) {
    this.eventId = eventId;
    this.provider = provider;
    this.receivedAt = Instant.now();
  }

  public String getEventId() {
    return eventId;
  }

  public String getProvider() {
    return provider;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }
}
