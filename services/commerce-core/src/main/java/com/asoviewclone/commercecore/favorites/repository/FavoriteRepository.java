package com.asoviewclone.commercecore.favorites.repository;

import com.asoviewclone.commercecore.favorites.model.Favorite;
import com.asoviewclone.commercecore.favorites.model.FavoriteId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

  List<Favorite> findByUserId(UUID userId);

  @Query("SELECT f FROM Favorite f WHERE f.userId = :userId AND f.productId IN :productIds")
  List<Favorite> findByUserIdAndProductIdIn(UUID userId, Collection<UUID> productIds);

  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  void deleteByUserIdAndProductId(UUID userId, UUID productId);
}
