package com.asoviewclone.commercecore.wallet.controller;

import com.asoviewclone.commercecore.security.AuthenticatedUser;
import com.asoviewclone.commercecore.wallet.service.WalletService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Owner-checked wallet pass endpoints. Both endpoints route through {@link
 * com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl#findTicketPassContextForOwner}
 * which throws {@code NotFoundException} for both "ticket does not exist" and "ticket belongs to
 * someone else" so cross-user accesses return 404 (never 403) and existence is never leaked. This
 * matches the contract of {@code GET /v1/orders/{id}/tickets}.
 *
 * <p><b>Production note:</b> the Apple .pkpass produced by this controller is signed with a
 * self-signed dev cert and is NOT acceptable to Apple Wallet without a real WWDR-chained Pass Type
 * ID cert. See PR runbook.
 */
@RestController
@RequestMapping("/v1/me/tickets")
public class WalletController {

  private final WalletService walletService;

  public WalletController(WalletService walletService) {
    this.walletService = walletService;
  }

  @GetMapping(value = "/{ticketId}/apple-pass", produces = "application/vnd.apple.pkpass")
  public ResponseEntity<byte[]> applePass(@PathVariable String ticketId) {
    byte[] pass = walletService.buildApplePass(ticketId, currentUserId());
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"asoview-ticket.pkpass\"")
        .body(pass);
  }

  @GetMapping("/{ticketId}/google-pass-link")
  public Map<String, String> googlePassLink(@PathVariable String ticketId) {
    String url = walletService.buildGoogleSaveUrl(ticketId, currentUserId());
    return Map.of("saveUrl", url);
  }

  private static String currentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
      throw new org.springframework.security.access.AccessDeniedException("unauthenticated");
    }
    return user.userId().toString();
  }
}
