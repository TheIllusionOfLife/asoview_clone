package com.asoviewclone.commercecore.orders.model;

import java.time.Instant;
import java.util.List;

public record Order(
    String orderId,
    String userId,
    OrderStatus status,
    String totalAmount,
    String currency,
    String idempotencyKey,
    List<OrderItem> items,
    Instant createdAt,
    Instant updatedAt) {}
