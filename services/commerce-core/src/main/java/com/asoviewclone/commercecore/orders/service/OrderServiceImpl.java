package com.asoviewclone.commercecore.orders.service;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

  private final OrderRepository orderRepository;
  private final InventoryService inventoryService;
  private final ProductVariantRepository productVariantRepository;

  public OrderServiceImpl(
      OrderRepository orderRepository,
      InventoryService inventoryService,
      ProductVariantRepository productVariantRepository) {
    this.orderRepository = orderRepository;
    this.inventoryService = inventoryService;
    this.productVariantRepository = productVariantRepository;
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

    // Hold inventory, look up prices, and save order.
    // If anything fails after holds are created, release them all.
    List<InventoryHold> holds = new ArrayList<>();
    try {
      // Hold inventory for each item
      for (CreateOrderItemRequest item : items) {
        InventoryHold hold = inventoryService.holdInventory(item.slotId(), userId, item.quantity());
        holds.add(hold);
      }

      // Look up variant prices from catalog and calculate total
      BigDecimal total = BigDecimal.ZERO;
      String currency = null;
      List<OrderItem> orderItems = new ArrayList<>();
      for (int i = 0; i < items.size(); i++) {
        CreateOrderItemRequest item = items.get(i);
        InventoryHold hold = holds.get(i);

        ProductVariant variant =
            productVariantRepository
                .findById(UUID.fromString(item.productVariantId()))
                .orElseThrow(
                    () -> new NotFoundException("ProductVariant", item.productVariantId()));

        // Derive and validate currency from variant
        if (currency == null) {
          currency = variant.getPriceCurrency();
        } else if (!currency.equals(variant.getPriceCurrency())) {
          throw new ValidationException(
              "Mixed currencies in order: " + currency + " and " + variant.getPriceCurrency());
        }

        String unitPrice = variant.getPriceAmount().toPlainString();
        BigDecimal itemTotal =
            variant.getPriceAmount().multiply(BigDecimal.valueOf(item.quantity()));
        total = total.add(itemTotal);

        orderItems.add(
            new OrderItem(
                null,
                null,
                item.productVariantId(),
                item.slotId(),
                item.quantity(),
                unitPrice,
                hold.holdId(),
                null));
      }

      return orderRepository.save(
          userId, total.toPlainString(), currency, idempotencyKey, orderItems);
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
      if (item.holdId() != null) {
        try {
          inventoryService.releaseHold(item.holdId());
        } catch (Exception ignored) {
          // Best effort: hold may have already expired
        }
      }
    }

    orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
  }
}
