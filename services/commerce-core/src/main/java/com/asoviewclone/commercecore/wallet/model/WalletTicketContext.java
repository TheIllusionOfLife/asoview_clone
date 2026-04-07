package com.asoviewclone.commercecore.wallet.model;

import java.time.Instant;

/**
 * Read-only projection passed from {@link
 * com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl} to the wallet
 * builders. Contains the union of fields needed by both Apple .pkpass and Google Wallet payload
 * templates.
 */
public record WalletTicketContext(
    String ticketPassId,
    String orderId,
    String userId,
    String productName,
    String venueName,
    Instant validFrom,
    Instant validUntil,
    String qrCodePayload) {}
