package com.asoviewclone.commercecore.points.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_balances")
public class PointBalance {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false)
  private long balance = 0L;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected PointBalance() {}

  public PointBalance(UUID userId) {
    this.userId = userId;
  }

  public UUID getUserId() {
    return userId;
  }

  public long getBalance() {
    return balance;
  }

  public void setBalance(long balance) {
    this.balance = balance;
    this.updatedAt = Instant.now();
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
