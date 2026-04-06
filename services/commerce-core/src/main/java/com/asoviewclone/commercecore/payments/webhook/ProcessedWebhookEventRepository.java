package com.asoviewclone.commercecore.payments.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookEventRepository
    extends JpaRepository<ProcessedWebhookEvent, ProcessedWebhookEventId> {}
