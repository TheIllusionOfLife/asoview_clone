package com.asoviewclone.commercecore.orders.controller.dto;

import java.util.List;

public record CreateOrderRequest(String idempotencyKey, List<OrderItemRequest> items) {

  public record OrderItemRequest(
      String productVariantId, String slotId, int quantity, String unitPrice) {}
}
