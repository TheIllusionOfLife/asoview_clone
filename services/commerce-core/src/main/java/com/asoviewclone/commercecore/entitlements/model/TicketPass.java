package com.asoviewclone.commercecore.entitlements.model;

import java.time.Instant;

public record TicketPass(
    String ticketPassId,
    String entitlementId,
    String qrCodePayload,
    TicketPassStatus status,
    String venueId,
    String tenantId,
    Instant usedAt,
    Instant createdAt) {}
