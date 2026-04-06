package com.asoviewclone.commercecore.entitlements.model;

import java.time.Instant;

/**
 * Read-side projection of a ticket pass joined with its parent entitlement. The frontend
 * /tickets/[orderId] page needs the entitlement's order id and validity window alongside the
 * ticket pass's QR payload, but the {@link TicketPass} record itself only carries pass-level
 * fields. This view is what {@code GET /v1/me/tickets} returns.
 */
public record TicketPassView(
    String ticketPassId,
    String entitlementId,
    String orderId,
    String qrCodePayload,
    TicketPassStatus status,
    Instant validFrom,
    Instant validUntil,
    Instant createdAt) {}
