package com.asoviewclone.commercecore.orders.controller.dto;

import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import java.util.List;

public record OrderResponse(
    String orderId,
    String userId,
    String status,
    String totalAmount,
    String currency,
    List<ItemResponse> items) {

  public static OrderResponse from(Order order) {
    return new OrderResponse(
        order.orderId(),
        order.userId(),
        order.status().name(),
        order.totalAmount(),
        order.currency(),
        order.items().stream().map(ItemResponse::from).toList());
  }

  public record ItemResponse(
      String orderItemId, String productVariantId, String slotId, long quantity, String unitPrice) {

    public static ItemResponse from(OrderItem item) {
      return new ItemResponse(
          item.orderItemId(),
          item.productVariantId(),
          item.slotId(),
          item.quantity(),
          item.unitPrice());
    }
  }
}
