package com.asoviewclone.commercecore.favorites.repository;

import com.asoviewclone.commercecore.favorites.model.Favorite;
import com.asoviewclone.commercecore.favorites.model.FavoriteId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoriteRepository extends JpaRepository<Favorite, FavoriteId> {

  List<Favorite> findByUserId(UUID userId);

  @Query("SELECT f FROM Favorite f WHERE f.userId = :userId AND f.productId IN :productIds")
  List<Favorite> findByUserIdAndProductIdIn(UUID userId, Collection<UUID> productIds);

  boolean existsByUserIdAndProductId(UUID userId, UUID productId);

  void deleteByUserIdAndProductId(UUID userId, UUID productId);

  /**
   * Atomic insert-or-noop for the (user_id, product_id) tuple, returning the row count (1 = first
   * to favorite this product, 0 = already favorited). Replaces the existsBy-then-save TOCTOU
   * pattern in {@code FavoriteService.addFavorite}: a concurrent writer would otherwise pass the
   * existsBy check and both INSERTs would race at flush time, the loser throwing {@link
   * org.springframework.dao.DataIntegrityViolationException} (uncaught → 500). The {@code @IdClass}
   * composite key has no {@code @GeneratedValue}, so {@link
   * org.springframework.data.jpa.repository.JpaRepository#save save} also defers the INSERT past
   * any try-catch.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO favorites(user_id, product_id, created_at)"
              + " VALUES(:userId, :productId, CURRENT_TIMESTAMP)"
              + " ON CONFLICT (user_id, product_id) DO NOTHING",
      nativeQuery = true)
  int insertIfMissing(@Param("userId") UUID userId, @Param("productId") UUID productId);
}
