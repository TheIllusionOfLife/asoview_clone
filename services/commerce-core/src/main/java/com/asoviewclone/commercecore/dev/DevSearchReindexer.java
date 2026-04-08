package com.asoviewclone.commercecore.dev;

import com.asoviewclone.commercecore.catalog.event.ProductIndexEventPublisher;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes {@link com.asoviewclone.commercecore.catalog.event.ProductUpsertedEvent} for every
 * seeded product so the search indexer (OpenSearch via AFTER_COMMIT listener) is populated on
 * local/dev startup. Only activates under the {@code dev} profile — {@code local} skips the reindex
 * because OpenSearch isn't part of the default docker-compose stack.
 *
 * <p>CLAUDE.md PR #21 rule: a publisher method consumed by
 * {@code @TransactionalEventListener(AFTER_COMMIT)} MUST itself be {@code @Transactional}, even if
 * it has no JPA writes, otherwise the listener is silently dropped. {@link #reindexAll()} below
 * carries the empty JPA tx that the listener hangs AFTER_COMMIT off of.
 */
@Component
@Profile("dev")
@Order(200) // after DevDataSeeder
public class DevSearchReindexer implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DevSearchReindexer.class);

  private final ProductRepository productRepository;
  private final ProductIndexEventPublisher publisher;

  public DevSearchReindexer(
      ProductRepository productRepository, ProductIndexEventPublisher publisher) {
    this.productRepository = productRepository;
    this.publisher = publisher;
  }

  // CLAUDE.md self-call rule: this entry method (called by Spring's
  // CommandLineRunner pipeline) is the proxy boundary. The
  // @Transactional must live HERE, not on a `reindexAll()` helper that
  // run() invokes via `this.reindexAll()` — that bypasses the proxy and
  // the AFTER_COMMIT listener silently drops every publishUpsert call.
  @Override
  @Transactional
  public void run(String... args) {
    List<Product> all = productRepository.findAll();
    for (Product p : all) {
      if (p.getId() != null) {
        publisher.publishUpsert(p.getId().toString());
      }
    }
    log.info("DevSearchReindexer: published {} ProductUpsertedEvents", all.size());
  }
}
