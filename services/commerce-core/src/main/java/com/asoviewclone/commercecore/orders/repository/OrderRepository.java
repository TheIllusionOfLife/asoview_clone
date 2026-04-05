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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    String orderId = UUID.randomUUID().toString();
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
      mutations.add(
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
              .to(ts)
              .build());
      savedItems.add(
          new OrderItem(
              itemId,
              orderId,
              item.productVariantId(),
              item.slotId(),
              item.quantity(),
              item.unitPrice(),
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
    List<Order> orders = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        orders.add(mapOrder(rs));
      }
    }
    return orders;
  }

  public void updateStatus(String orderId, OrderStatus newStatus) {
    Instant now = clockProvider.now();
    databaseClient.write(
        List.of(
            Mutation.newUpdateBuilder("orders")
                .set("order_id")
                .to(orderId)
                .set("status")
                .to(newStatus.name())
                .set("updated_at")
                .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
                .build()));
  }

  private List<OrderItem> findItemsByOrderId(String orderId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT order_item_id, order_id, product_variant_id, slot_id,"
                    + " quantity, unit_price, created_at"
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
        rs.getTimestamp("created_at").toDate().toInstant());
  }
}
