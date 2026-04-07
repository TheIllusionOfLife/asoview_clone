package com.asoviewclone.commercecore.favorites.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FavoriteId implements Serializable {

  private UUID userId;
  private UUID productId;

  public FavoriteId() {}

  public FavoriteId(UUID userId, UUID productId) {
    this.userId = userId;
    this.productId = productId;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getProductId() {
    return productId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FavoriteId that)) return false;
    return Objects.equals(userId, that.userId) && Objects.equals(productId, that.productId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, productId);
  }
}
