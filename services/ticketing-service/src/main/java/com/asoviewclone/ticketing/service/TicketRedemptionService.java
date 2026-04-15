package com.asoviewclone.ticketing.service;

import com.asoviewclone.ticketing.exception.RateLimitedException;
import com.asoviewclone.ticketing.model.RedeemResult;
import com.asoviewclone.ticketing.ratelimit.RedeemRateLimiter;
import com.asoviewclone.ticketing.repository.TicketPassRedeemRepository;
import com.asoviewclone.ticketing.security.ScannerPrincipal;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class TicketRedemptionService {

  private final TicketPassRedeemRepository repository;
  private final RedeemRateLimiter rateLimiter;

  public TicketRedemptionService(
      TicketPassRedeemRepository repository, RedeemRateLimiter rateLimiter) {
    this.repository = repository;
    this.rateLimiter = rateLimiter;
  }

  public RedeemResult redeem(
      String qrCodePayload, String scannerDeviceId, String sourceIp, String idempotencyKey) {
    ScannerPrincipal principal = currentScanner();
    if (!rateLimiter.tryAcquire(principal.userId(), sourceIp, qrCodePayload)) {
      throw new RateLimitedException("Rate limit exceeded");
    }
    return repository.redeemAtomically(
        qrCodePayload,
        principal.userId(),
        scannerDeviceId,
        principal.scannerVenues(),
        principal.tenantId(),
        idempotencyKey,
        sourceIp);
  }

  public void revoke(String passId) {
    repository.revokeAtomically(passId);
  }

  private static ScannerPrincipal currentScanner() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof ScannerPrincipal sp)) {
      return new ScannerPrincipal(null, null, Set.of(), null);
    }
    return sp;
  }
}
