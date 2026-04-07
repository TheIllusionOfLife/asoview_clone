package com.asoviewclone.commercecore.points.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_discounts")
public class OrderDiscount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "order_id", nullable = false, unique = true)
  private String orderId;

  @Column(name = "discount_type", nullable = false)
  private String discountType;

  @Column(name = "amount_jpy", nullable = false)
  private long amountJpy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected OrderDiscount() {}

  public OrderDiscount(String orderId, String discountType, long amountJpy) {
    this.orderId = orderId;
    this.discountType = discountType;
    this.amountJpy = amountJpy;
  }

  public UUID getId() {
    return id;
  }

  public String getOrderId() {
    return orderId;
  }

  public String getDiscountType() {
    return discountType;
  }

  public long getAmountJpy() {
    return amountJpy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
