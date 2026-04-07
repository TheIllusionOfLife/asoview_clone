package com.asoviewclone.commercecore.payments.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedWebhookEventRepository
    extends JpaRepository<ProcessedWebhookEvent, ProcessedWebhookEventId> {

  /**
   * Insert-first idempotency gate. Returns 1 if this is the first time we've seen {@code (provider,
   * event_id)}, 0 if a row already exists.
   *
   * <p>Replaces {@code save(new ProcessedWebhookEvent(...))} which is broken for replay protection
   * because {@link ProcessedWebhookEvent} has assigned {@code @Id} fields (no
   * {@code @GeneratedValue}), so Spring Data's {@code isNew()} returns {@code false} and {@code
   * save()} routes through {@code merge()} — which does a SELECT, finds the existing row, and
   * silently UPDATEs it. No {@link org.springframework.dao.DataIntegrityViolationException} is
   * thrown for sequential retries from a provider, defeating the entire replay guard. Concurrent
   * inserts still throw but that's the rare case; provider sequential retries are the common case.
   *
   * <p>This native INSERT ... ON CONFLICT DO NOTHING is atomic against the unique key and returns
   * the row count, mirroring the {@code FavoriteRepository.insertIfMissing} / {@code
   * PointLedgerRepository.insertIfMissing} pattern from PR #21.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO processed_webhook_events (provider, event_id, received_at) "
              + "VALUES (:provider, :eventId, NOW()) "
              + "ON CONFLICT (provider, event_id) DO NOTHING",
      nativeQuery = true)
  int insertIfMissing(@Param("provider") String provider, @Param("eventId") String eventId);
}
