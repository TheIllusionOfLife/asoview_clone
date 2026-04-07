package com.asoviewclone.commercecore.favorites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.favorites.model.Favorite;
import com.asoviewclone.commercecore.favorites.repository.FavoriteRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FavoriteServiceTest {

  private FavoriteRepository repo;
  private FavoriteService service;
  private final UUID userId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(FavoriteRepository.class);
    service = new FavoriteService(repo);
  }

  @Test
  void addFavorite_idempotent() {
    when(repo.existsByUserIdAndProductId(userId, productId)).thenReturn(false, true);
    service.addFavorite(userId, productId);
    service.addFavorite(userId, productId);
    verify(repo).save(any(Favorite.class));
  }

  @Test
  void resolveFavoritedProductIds_batch() {
    UUID p2 = UUID.randomUUID();
    when(repo.findByUserIdAndProductIdIn(userId, List.of(productId, p2)))
        .thenReturn(List.of(new Favorite(userId, productId)));
    Set<UUID> favorited = service.resolveFavoritedProductIds(userId, List.of(productId, p2));
    assertThat(favorited).containsExactly(productId);
  }

  @Test
  void resolveFavoritedProductIds_nullUser_returnsEmpty() {
    assertThat(service.resolveFavoritedProductIds(null, List.of(productId))).isEmpty();
    verify(repo, never()).findByUserIdAndProductIdIn(any(), any());
  }
}
