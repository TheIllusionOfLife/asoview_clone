package com.asoviewclone.commercecore.identity.controller;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import com.asoviewclone.commercecore.identity.model.User;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.asoviewclone.common.error.NotFoundException;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /v1/me} — the authenticated user's profile. Consumed by web app header + mypage. */
@RestController
@RequestMapping("/v1/me")
public class MeController {

  private final UserRepository userRepository;

  public MeController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping
  public MeResponse getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
    User user =
        userRepository
            .findById(principal.userId())
            .orElseThrow(() -> new NotFoundException("User", principal.userId().toString()));
    return new MeResponse(
        user.getId(),
        user.getFirebaseUid(),
        user.getEmail(),
        user.getDisplayName(),
        principal.tenantRoles());
  }

  public record MeResponse(
      UUID userId,
      String firebaseUid,
      String email,
      String displayName,
      Map<UUID, TenantRole> tenantRoles) {}
}
