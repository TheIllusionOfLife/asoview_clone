package com.asoviewclone.commercecore.points.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_ledger")
public class PointLedgerEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private long delta;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PointReason reason;

  @Column(name = "order_id")
  private String orderId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected PointLedgerEntry() {}

  public PointLedgerEntry(UUID userId, long delta, PointReason reason, String orderId) {
    this.userId = userId;
    this.delta = delta;
    this.reason = reason;
    this.orderId = orderId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public long getDelta() {
    return delta;
  }

  public PointReason getReason() {
    return reason;
  }

  public String getOrderId() {
    return orderId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
