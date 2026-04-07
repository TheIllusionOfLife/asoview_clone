package com.asoviewclone.commercecore.favorites.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorites")
@IdClass(FavoriteId.class)
public class Favorite {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "product_id")
  private UUID productId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  protected Favorite() {}

  public Favorite(UUID userId, UUID productId) {
    this.userId = userId;
    this.productId = productId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProductId() {
    return productId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
