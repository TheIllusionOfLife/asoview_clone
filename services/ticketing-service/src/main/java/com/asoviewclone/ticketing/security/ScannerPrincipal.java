package com.asoviewclone.ticketing.security;

import java.util.Set;

/**
 * Authenticated operator: Firebase UID, tenant claim, venue allow-list, session id surrogate. Held
 * as {@link org.springframework.security.core.Authentication#getPrincipal()}. Defensive {@code
 * Set.copyOf} in the canonical constructor prevents post-construction mutation of the venue
 * allow-list (authorization state).
 */
public record ScannerPrincipal(
    String userId, String tenantId, Set<String> scannerVenues, String sessionId) {
  public ScannerPrincipal {
    scannerVenues = scannerVenues == null ? Set.of() : Set.copyOf(scannerVenues);
  }
}
