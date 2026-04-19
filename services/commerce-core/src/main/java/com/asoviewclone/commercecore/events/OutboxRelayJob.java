package com.asoviewclone.commercecore.events;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import com.asoviewclone.commercecore.events.repository.OutboxEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls unpublished outbox rows and publishes them to Pub/Sub. Each row is published and marked
 * individually so a single failure does not block the entire batch. No @Transactional on this
 * method: markPublished() is transactional per-call, and we must not hold a DB connection open
 * during Pub/Sub network calls.
 *
 * <p>Gated on non-empty {@code spring.cloud.gcp.project-id} via {@code @ConditionalOnExpression}.
 * The older {@code @ConditionalOnBean(PubSubPublisher.class)} had an ordering hazard on
 * {@code @Component} beans — condition evaluated before {@code GcpPubSubPublisher} was registered,
 * so the job silently no-op'd in production (see PR #77 post-merge verification). Matches the same
 * safe pattern {@code GcpPubSubPublisher} and {@code ProductUpsertedSubscriber} use.
 */
@Component
@ConditionalOnExpression("'${spring.cloud.gcp.project-id:}' != ''")
public class OutboxRelayJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);
  private static final int BATCH_SIZE = 100;
  private static final int MAX_ATTEMPTS = 5;

  private final OutboxEventRepository outboxRepository;
  private final PubSubPublisher publisher;

  public OutboxRelayJob(OutboxEventRepository outboxRepository, PubSubPublisher publisher) {
    this.outboxRepository = outboxRepository;
    this.publisher = publisher;
  }

  @Scheduled(fixedDelay = 1000)
  public void relay() {
    List<OutboxEvent> pending = outboxRepository.findUnpublished(BATCH_SIZE);
    if (pending.isEmpty()) {
      return;
    }

    int published = 0;
    for (OutboxEvent event : pending) {
      try {
        publisher.publish(event.getTopic(), event.getEventId(), event.getPayload());
        int rows = outboxRepository.markPublished(event.getId());
        if (rows == 1) {
          published++;
        } else {
          log.debug("Outbox event {} already published by another runner", event.getEventId());
        }
      } catch (Exception ex) {
        // Atomically increment attempt_count and quarantine (set failed_at) if the new
        // count reaches MAX_ATTEMPTS. Uses the DB value, not the stale in-memory entity,
        // so concurrent runners cannot overshoot the cap.
        outboxRepository.incrementAttemptAndMaybeQuarantine(event.getId(), MAX_ATTEMPTS);
        log.error(
            "Outbox relay failed for event id={} type={} topic={} (will quarantine at {} attempts)",
            event.getEventId(),
            event.getEventType(),
            event.getTopic(),
            MAX_ATTEMPTS,
            ex);
      }
    }
    if (published > 0) {
      log.info("Outbox relay published {} of {} pending events", published, pending.size());
    }
  }
}
