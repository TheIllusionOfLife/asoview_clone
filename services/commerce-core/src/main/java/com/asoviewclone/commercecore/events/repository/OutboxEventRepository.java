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

  /**
   * Atomically increment attempt count and quarantine (set failed_at) if the new count reaches
   * maxAttempts. Returns the number of rows quarantined (0 or 1). Uses the DB value of
   * attempt_count, not the in-memory entity, so concurrent runners cannot overshoot the cap.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "UPDATE outbox_events SET attempt_count = attempt_count + 1,"
              + " failed_at = CASE WHEN attempt_count + 1 >= :maxAttempts"
              + " THEN CURRENT_TIMESTAMP ELSE failed_at END"
              + " WHERE id = :id",
      nativeQuery = true)
  int incrementAttemptAndMaybeQuarantine(
      @Param("id") UUID id, @Param("maxAttempts") int maxAttempts);
}
