package com.asoviewclone.commercecore.favorites.controller;

import com.asoviewclone.commercecore.favorites.service.FavoriteService;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
public class FavoriteController {

  private final FavoriteService favoriteService;

  public FavoriteController(FavoriteService favoriteService) {
    this.favoriteService = favoriteService;
  }

  @PutMapping("/me/favorites/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void addFavorite(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID productId) {
    favoriteService.addFavorite(user.userId(), productId);
  }

  @DeleteMapping("/me/favorites/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeFavorite(
      @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID productId) {
    favoriteService.removeFavorite(user.userId(), productId);
  }

  @GetMapping("/me/favorites")
  public List<UUID> listFavorites(@AuthenticationPrincipal AuthenticatedUser user) {
    return favoriteService.listUserFavoriteProductIds(user.userId());
  }
}
