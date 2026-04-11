package com.asoviewclone.commercecore.orders.controller.dto;

import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OrderResponse(
    String orderId,
    String userId,
    String status,
    String totalAmount,
    String currency,
    List<ItemResponse> items) {

  /**
   * Build an OrderResponse enriching each item with its parent productId. The caller supplies a map
   * from variant id to product id (batch-fetched from the catalog) so resolution is O(1) per item
   * instead of N+1.
   */
  public static OrderResponse from(Order order, Map<UUID, UUID> variantToProductId) {
    return new OrderResponse(
        order.orderId(),
        order.userId(),
        order.status().name(),
        order.totalAmount(),
        order.currency(),
        order.items().stream().map(item -> ItemResponse.from(item, variantToProductId)).toList());
  }

  public record ItemResponse(
      String productId,
      String orderItemId,
      String productVariantId,
      String slotId,
      long quantity,
      String unitPrice) {

    public static ItemResponse from(OrderItem item, Map<UUID, UUID> variantToProductId) {
      UUID variantUuid = UUID.fromString(item.productVariantId());
      UUID productUuid = variantToProductId.get(variantUuid);
      return new ItemResponse(
          productUuid != null ? productUuid.toString() : null,
          item.orderItemId(),
          item.productVariantId(),
          item.slotId(),
          item.quantity(),
          item.unitPrice());
    }
  }
}
