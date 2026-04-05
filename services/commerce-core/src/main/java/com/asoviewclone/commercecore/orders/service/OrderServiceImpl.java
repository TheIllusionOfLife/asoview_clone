package com.asoviewclone.commercecore.orders.service;

import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final InventoryService inventoryService;

  public OrderServiceImpl(OrderRepository orderRepository, InventoryService inventoryService) {
    this.orderRepository = orderRepository;
    this.inventoryService = inventoryService;
  }

  @Override
  public Order createOrder(
      String userId, String idempotencyKey, List<CreateOrderItemRequest> items) {
    if (items == null || items.isEmpty()) {
      throw new ValidationException("Order must have at least one item");
    }

    // Idempotency check
    Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return existing.get();
    }

    // Hold inventory for each item
    List<InventoryHold> holds = new ArrayList<>();
    try {
      for (CreateOrderItemRequest item : items) {
        InventoryHold hold = inventoryService.holdInventory(item.slotId(), userId, item.quantity());
        holds.add(hold);
      }
    } catch (Exception e) {
      // Release any holds that were created
      for (InventoryHold hold : holds) {
        try {
          inventoryService.releaseHold(hold.holdId());
        } catch (Exception ignored) {
          // Best effort release
        }
      }
      throw e;
    }

    // Calculate total
    BigDecimal total = BigDecimal.ZERO;
    List<OrderItem> orderItems = new ArrayList<>();
    for (CreateOrderItemRequest item : items) {
      BigDecimal itemTotal =
          new BigDecimal(item.unitPrice()).multiply(BigDecimal.valueOf(item.quantity()));
      total = total.add(itemTotal);
      orderItems.add(
          new OrderItem(
              null,
              null,
              item.productVariantId(),
              item.slotId(),
              item.quantity(),
              item.unitPrice(),
              null));
    }

    return orderRepository.save(userId, total.toPlainString(), "JPY", idempotencyKey, orderItems);
  }

  @Override
  public Order getOrder(String orderId) {
    return orderRepository.findById(orderId);
  }

  @Override
  public List<Order> listUserOrders(String userId) {
    return orderRepository.findByUserId(userId);
  }

  @Override
  public void cancelOrder(String orderId) {
    Order order = orderRepository.findById(orderId);
    if (!order.status().canTransitionTo(OrderStatus.CANCELLED)) {
      throw new ValidationException("Cannot cancel order in status " + order.status());
    }

    // Release inventory holds for each item
    for (OrderItem item : order.items()) {
      // In a real implementation, we'd look up the hold by slot+user
      // For now, holds expire via TTL
    }

    orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
  }
}
