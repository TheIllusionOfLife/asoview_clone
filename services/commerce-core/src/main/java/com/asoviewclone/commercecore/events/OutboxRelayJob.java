package com.asoviewclone.commercecore.events;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import com.asoviewclone.commercecore.events.repository.OutboxEventRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls unpublished outbox rows and publishes them to Pub/Sub. Each row is published and marked
 * individually so a single failure does not block the entire batch. No @Transactional on this
 * method: markPublished() is transactional per-call, and we must not hold a DB connection open
 * during Pub/Sub network calls.
 */
@Component
public class OutboxRelayJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);
  private static final int BATCH_SIZE = 100;

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
        outboxRepository.markPublished(event.getId());
        published++;
      } catch (Exception ex) {
        log.error(
            "Outbox relay failed for event id={} type={} topic={}",
            event.getEventId(),
            event.getEventType(),
            event.getTopic(),
            ex);
      }
    }
    if (published > 0) {
      log.info("Outbox relay published {} of {} pending events", published, pending.size());
    }
  }
}
