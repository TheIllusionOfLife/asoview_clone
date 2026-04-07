package com.asoviewclone.commercecore.wallet.service;

import com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl;
import com.asoviewclone.commercecore.wallet.model.WalletTicketContext;
import org.springframework.stereotype.Service;

/**
 * Owner-checked wallet pass facade. Resolves a {@link WalletTicketContext} via {@link
 * EntitlementServiceImpl#findTicketPassContextForOwner(String, String)} (which throws {@code
 * NotFoundException} for both "doesn't exist" and "exists but belongs to someone else") then
 * delegates to the per-platform builder.
 */
@Service
public class WalletService {

  private final EntitlementServiceImpl entitlementService;
  private final AppleWalletPassBuilder appleBuilder;
  private final GoogleWalletJwtBuilder googleBuilder;

  public WalletService(
      EntitlementServiceImpl entitlementService,
      AppleWalletPassBuilder appleBuilder,
      GoogleWalletJwtBuilder googleBuilder) {
    this.entitlementService = entitlementService;
    this.appleBuilder = appleBuilder;
    this.googleBuilder = googleBuilder;
  }

  public byte[] buildApplePass(String ticketPassId, String userId) {
    WalletTicketContext ctx =
        entitlementService.findTicketPassContextForOwner(ticketPassId, userId);
    return appleBuilder.build(ctx);
  }

  public String buildGoogleSaveUrl(String ticketPassId, String userId) {
    WalletTicketContext ctx =
        entitlementService.findTicketPassContextForOwner(ticketPassId, userId);
    return googleBuilder.buildSaveUrl(ctx);
  }
}
