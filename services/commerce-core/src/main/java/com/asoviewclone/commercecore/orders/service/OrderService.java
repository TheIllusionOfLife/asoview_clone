package com.asoviewclone.commercecore.orders.service;

import com.asoviewclone.commercecore.orders.model.Order;
import java.util.List;

public interface OrderService {

  Order createOrder(String userId, String idempotencyKey, List<CreateOrderItemRequest> items);

  Order getOrder(String orderId);

  List<Order> listUserOrders(String userId);

  void cancelOrder(String orderId);

  record CreateOrderItemRequest(
      String productVariantId, String slotId, int quantity, String unitPrice) {}
}
