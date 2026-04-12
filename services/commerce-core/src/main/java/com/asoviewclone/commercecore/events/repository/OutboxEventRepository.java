package com.asoviewclone.commercecore.events.repository;

import com.asoviewclone.commercecore.events.model.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Query("SELECT e FROM OutboxEvent e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
  List<OutboxEvent> findUnpublished();

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("UPDATE OutboxEvent e SET e.publishedAt = CURRENT_TIMESTAMP WHERE e.id = :id")
  int markPublished(@Param("id") UUID id);
}
