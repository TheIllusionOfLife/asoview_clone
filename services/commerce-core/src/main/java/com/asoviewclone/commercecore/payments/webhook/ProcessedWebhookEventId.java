package com.asoviewclone.commercecore.payments.webhook;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite key for {@link ProcessedWebhookEvent}. The replay-protection table is keyed on {@code
 * (provider, event_id)} since Stripe and PayPay can mint identical event id strings — see V9
 * migration. Must be a public no-arg-constructible {@link Serializable} for JPA.
 */
public class ProcessedWebhookEventId implements Serializable {

  private String provider;
  private String eventId;

  public ProcessedWebhookEventId() {}

  public ProcessedWebhookEventId(String provider, String eventId) {
    this.provider = provider;
    this.eventId = eventId;
  }

  public String getProvider() {
    return provider;
  }

  public String getEventId() {
    return eventId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProcessedWebhookEventId that)) return false;
    return Objects.equals(provider, that.provider) && Objects.equals(eventId, that.eventId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(provider, eventId);
  }
}
