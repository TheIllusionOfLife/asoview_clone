package com.asoviewclone.commercecore.events.repository;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Fetch unpublished, non-quarantined outbox rows ordered by creation time. Concurrent relay
   * runners may read the same rows: this is safe because markPublished uses a CAS guard (WHERE
   * published_at IS NULL) and consumers deduplicate on event_id.
   */
  @Query(
      value =
          "SELECT * FROM outbox_events"
              + " WHERE published_at IS NULL AND failed_at IS NULL"
              + " ORDER BY created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<OutboxEvent> findUnpublished(@Param("limit") int limit);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE OutboxEvent e SET e.publishedAt = CURRENT_TIMESTAMP"
          + " WHERE e.id = :id AND e.publishedAt IS NULL")
  int markPublished(@Param("id") UUID id);

  /** Increment attempt count after a failed publish. */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE OutboxEvent e SET e.attemptCount = e.attemptCount + 1 WHERE e.id = :id")
  void incrementAttemptCount(@Param("id") UUID id);

  /** Quarantine a row that has exceeded max attempts so it stops blocking the queue. */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE OutboxEvent e SET e.failedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
  void quarantine(@Param("id") UUID id);
}
