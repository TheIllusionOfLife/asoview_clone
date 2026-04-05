package com.asoviewclone.commercecore.orders.model;

import java.time.Instant;

public record OrderItem(
    String orderItemId,
    String orderId,
    String productVariantId,
    String slotId,
    long quantity,
    String unitPrice,
    Instant createdAt) {}
