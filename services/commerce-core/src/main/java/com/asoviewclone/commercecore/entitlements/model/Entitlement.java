package com.asoviewclone.commercecore.entitlements.model;

import java.time.Instant;

public record Entitlement(
    String entitlementId,
    String orderId,
    String orderItemId,
    String userId,
    String productVariantId,
    EntitlementType type,
    EntitlementStatus status,
    Instant validFrom,
    Instant validUntil,
    Instant createdAt) {}
