package com.asoviewclone.commercecore.orders.repository;

import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.common.error.NotFoundException;
import com.asoviewclone.common.time.ClockProvider;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

  private final DatabaseClient databaseClient;
  private final ClockProvider clockProvider;

  public OrderRepository(DatabaseClient databaseClient, ClockProvider clockProvider) {
    this.databaseClient = databaseClient;
    this.clockProvider = clockProvider;
  }

  public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT order_id, user_id, status, total_amount, currency,"
                    + " idempotency_key, created_at, updated_at"
                    + " FROM orders WHERE idempotency_key = @key")
            .bind("key")
            .to(idempotencyKey)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        Order order = mapOrder(rs);
        List<OrderItem> items = findItemsByOrderId(order.orderId());
        return Optional.of(
            new Order(
                order.orderId(),
                order.userId(),
                order.status(),
                order.totalAmount(),
                order.currency(),
                order.idempotencyKey(),
                items,
                order.createdAt(),
                order.updatedAt()));
      }
    }
    return Optional.empty();
  }

  public Order save(
      String userId,
      String totalAmount,
      String currency,
      String idempotencyKey,
      List<OrderItem> items) {
    return saveWithId(
        UUID.randomUUID().toString(), userId, totalAmount, currency, idempotencyKey, items);
  }

  /**
   * Save an order with a caller-supplied id. Used by {@code OrderServiceImpl} when it needs to
   * pre-generate the order id (e.g. so a points-burn ledger row can pin to a stable id BEFORE the
   * Spanner write happens — see PR #21 review C4).
   */
  public Order saveWithId(
      String orderId,
      String userId,
      String totalAmount,
      String currency,
      String idempotencyKey,
      List<OrderItem> items) {
    Instant now = clockProvider.now();
    Timestamp ts = Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0);

    List<Mutation> mutations = new ArrayList<>();
    mutations.add(
        Mutation.newInsertBuilder("orders")
            .set("order_id")
            .to(orderId)
            .set("user_id")
            .to(userId)
            .set("status")
            .to(OrderStatus.PENDING.name())
            .set("total_amount")
            .to(totalAmount)
            .set("currency")
            .to(currency)
            .set("idempotency_key")
            .to(idempotencyKey)
            .set("created_at")
            .to(ts)
            .set("updated_at")
            .to(ts)
            .build());

    List<OrderItem> savedItems = new ArrayList<>();
    for (OrderItem item : items) {
      String itemId = UUID.randomUUID().toString();
      Mutation.WriteBuilder itemBuilder =
          Mutation.newInsertBuilder("order_items")
              .set("order_item_id")
              .to(itemId)
              .set("order_id")
              .to(orderId)
              .set("product_variant_id")
              .to(item.productVariantId())
              .set("slot_id")
              .to(item.slotId())
              .set("quantity")
              .to(item.quantity())
              .set("unit_price")
              .to(item.unitPrice())
              .set("created_at")
              .to(ts);
      if (item.holdId() != null) {
        itemBuilder.set("hold_id").to(item.holdId());
      }
      mutations.add(itemBuilder.build());
      savedItems.add(
          new OrderItem(
              itemId,
              orderId,
              item.productVariantId(),
              item.slotId(),
              item.quantity(),
              item.unitPrice(),
              item.holdId(),
              now));
    }

    databaseClient.write(mutations);
    return new Order(
        orderId,
        userId,
        OrderStatus.PENDING,
        totalAmount,
        currency,
        idempotencyKey,
        savedItems,
        now,
        now);
  }

  public Order findById(String orderId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT order_id, user_id, status, total_amount, currency,"
                    + " idempotency_key, created_at, updated_at"
                    + " FROM orders WHERE order_id = @id")
            .bind("id")
            .to(orderId)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        Order order = mapOrder(rs);
        List<OrderItem> items = findItemsByOrderId(orderId);
        return new Order(
            order.orderId(),
            order.userId(),
            order.status(),
            order.totalAmount(),
            order.currency(),
            order.idempotencyKey(),
            items,
            order.createdAt(),
            order.updatedAt());
      }
    }
    throw new NotFoundException("Order", orderId);
  }

  public List<Order> findByUserId(String userId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT order_id, user_id, status, total_amount, currency,"
                    + " idempotency_key, created_at, updated_at"
                    + " FROM orders WHERE user_id = @uid ORDER BY created_at DESC")
            .bind("uid")
            .to(userId)
            .build();
    List<Order> shellOrders = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        shellOrders.add(mapOrder(rs));
      }
    }

    if (shellOrders.isEmpty()) {
      return List.of();
    }

    // Batch-fetch items for all orders to avoid N+1 queries
    List<String> orderIds = shellOrders.stream().map(Order::orderId).collect(Collectors.toList());
    Map<String, List<OrderItem>> itemsByOrderId = findItemsByOrderIds(orderIds);

    List<Order> orders = new ArrayList<>();
    for (Order o : shellOrders) {
      orders.add(
          new Order(
              o.orderId(),
              o.userId(),
              o.status(),
              o.totalAmount(),
              o.currency(),
              o.idempotencyKey(),
              itemsByOrderId.getOrDefault(o.orderId(), List.of()),
              o.createdAt(),
              o.updatedAt()));
    }
    return orders;
  }

  /**
   * Compare-and-swap status update. Reads the current status in a read-write transaction, only
   * writes the new status if the current status matches {@code expected}. Returns true on success,
   * false if the status did not match.
   */
  /**
   * Returns the set of {@code product_variant_id}s referenced by PAID orders for the given user.
   * Pushes both the user filter and the {@code status='PAID'} filter into Spanner so we don't fetch
   * every order in memory just to filter it. (PR #21 review M1 from Gemini.)
   */
  public java.util.Set<String> findPaidVariantIdsByUserId(String userId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT DISTINCT oi.product_variant_id"
                    + " FROM orders o JOIN order_items oi ON o.order_id = oi.order_id"
                    + " WHERE o.user_id = @uid AND o.status = 'PAID'")
            .bind("uid")
            .to(userId)
            .build();
    java.util.Set<String> result = new java.util.HashSet<>();
    try (com.google.cloud.spanner.ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        result.add(rs.getString("product_variant_id"));
      }
    }
    return result;
  }

  public boolean updateStatusIf(String orderId, OrderStatus expected, OrderStatus newStatus) {
    Boolean result =
        databaseClient
            .readWriteTransaction()
            .run(
                tx -> {
                  Statement stmt =
                      Statement.newBuilder("SELECT status FROM orders WHERE order_id = @id")
                          .bind("id")
                          .to(orderId)
                          .build();
                  OrderStatus current = null;
                  try (ResultSet rs = tx.executeQuery(stmt)) {
                    if (rs.next()) {
                      current = OrderStatus.valueOf(rs.getString("status"));
                    }
                  }
                  if (current == null || current != expected) {
                    return Boolean.FALSE;
                  }
                  Instant now = clockProvider.now();
                  tx.buffer(
                      Mutation.newUpdateBuilder("orders")
                          .set("order_id")
                          .to(orderId)
                          .set("status")
                          .to(newStatus.name())
                          .set("updated_at")
                          .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
                          .build());
                  return Boolean.TRUE;
                });
    return Boolean.TRUE.equals(result);
  }

  private Map<String, List<OrderItem>> findItemsByOrderIds(List<String> orderIds) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT order_item_id, order_id, product_variant_id, slot_id,"
                    + " quantity, unit_price, hold_id, created_at"
                    + " FROM order_items WHERE order_id IN UNNEST(@oids)")
            .bind("oids")
            .toStringArray(orderIds)
            .build();
    Map<String, List<OrderItem>> result = new HashMap<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        OrderItem item = mapItem(rs);
        result.computeIfAbsent(item.orderId(), k -> new ArrayList<>()).add(item);
      }
    }
    return result;
  }

  private List<OrderItem> findItemsByOrderId(String orderId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT order_item_id, order_id, product_variant_id, slot_id,"
                    + " quantity, unit_price, hold_id, created_at"
                    + " FROM order_items WHERE order_id = @oid")
            .bind("oid")
            .to(orderId)
            .build();
    List<OrderItem> items = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        items.add(mapItem(rs));
      }
    }
    return items;
  }

  private Order mapOrder(ResultSet rs) {
    return new Order(
        rs.getString("order_id"),
        rs.getString("user_id"),
        OrderStatus.valueOf(rs.getString("status")),
        rs.getString("total_amount"),
        rs.getString("currency"),
        rs.getString("idempotency_key"),
        List.of(),
        rs.getTimestamp("created_at").toDate().toInstant(),
        rs.getTimestamp("updated_at").toDate().toInstant());
  }

  private OrderItem mapItem(ResultSet rs) {
    return new OrderItem(
        rs.getString("order_item_id"),
        rs.getString("order_id"),
        rs.getString("product_variant_id"),
        rs.getString("slot_id"),
        rs.getLong("quantity"),
        rs.getString("unit_price"),
        rs.isNull("hold_id") ? null : rs.getString("hold_id"),
        rs.getTimestamp("created_at").toDate().toInstant());
  }
}
