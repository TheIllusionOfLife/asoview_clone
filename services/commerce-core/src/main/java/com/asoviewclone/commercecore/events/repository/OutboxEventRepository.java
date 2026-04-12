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

  @Query(
      value =
          "SELECT * FROM outbox_events WHERE published_at IS NULL ORDER BY created_at ASC LIMIT :limit",
      nativeQuery = true)
  List<OutboxEvent> findUnpublished(@Param("limit") int limit);

  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE OutboxEvent e SET e.publishedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
  int markPublished(@Param("id") UUID id);
}
