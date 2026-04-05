package com.asoviewclone.commercecore.entitlements.model;

import java.time.Instant;

public record TicketPass(
    String ticketPassId,
    String entitlementId,
    String qrCodePayload,
    TicketPassStatus status,
    Instant usedAt,
    Instant createdAt) {}
