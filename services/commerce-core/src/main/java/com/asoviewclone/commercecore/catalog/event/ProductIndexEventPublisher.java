package com.asoviewclone.commercecore.catalog.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around Spring's {@link ApplicationEventPublisher} that fires {@link
 * ProductUpsertedEvent} after a product write. The actual cross-store dispatch lives in {@link
 * ProductIndexEventListener} which uses {@code @TransactionalEventListener(AFTER_COMMIT)} so the
 * search index is only touched once the JPA transaction has actually committed.
 *
 * <p>Currently the listener is log-only. The next PR wires it to Spring Cloud GCP Pub/Sub.
 */
@Component
public class ProductIndexEventPublisher {

  private final ApplicationEventPublisher publisher;

  public ProductIndexEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  public void publishUpsert(String productId) {
    publisher.publishEvent(new ProductUpsertedEvent(productId));
  }
}
