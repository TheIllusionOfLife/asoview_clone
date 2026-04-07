package com.asoviewclone.commercecore.entitlements.model;

import java.time.Instant;

/**
 * Single-row projection joining a ticket pass to its parent entitlement so callers can resolve
 * ownership, order id, validity window, and product variant in a single Spanner read.
 */
public record TicketPassWithEntitlement(
    String ticketPassId,
    String entitlementId,
    String qrCodePayload,
    TicketPassStatus status,
    String orderId,
    String userId,
    String productVariantId,
    Instant validFrom,
    Instant validUntil) {}
