package com.asoviewclone.commercecore.favorites.service;

import com.asoviewclone.commercecore.favorites.model.Favorite;
import com.asoviewclone.commercecore.favorites.model.FavoriteId;
import com.asoviewclone.commercecore.favorites.repository.FavoriteRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;

  public FavoriteService(FavoriteRepository favoriteRepository) {
    this.favoriteRepository = favoriteRepository;
  }

  public void addFavorite(UUID userId, UUID productId) {
    if (favoriteRepository.existsByUserIdAndProductId(userId, productId)) {
      return; // idempotent
    }
    favoriteRepository.save(new Favorite(userId, productId));
  }

  public void removeFavorite(UUID userId, UUID productId) {
    favoriteRepository.deleteById(new FavoriteId(userId, productId));
  }

  @Transactional(readOnly = true)
  public List<UUID> listUserFavoriteProductIds(UUID userId) {
    return favoriteRepository.findByUserId(userId).stream()
        .map(Favorite::getProductId)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public Set<UUID> resolveFavoritedProductIds(UUID userId, Collection<UUID> productIds) {
    if (userId == null || productIds == null || productIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        favoriteRepository.findByUserIdAndProductIdIn(userId, productIds).stream()
            .map(Favorite::getProductId)
            .collect(Collectors.toSet()));
  }
}
