package com.asoviewclone.ticketing.security;

import java.util.Set;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Authenticated operator: Firebase UID, tenant claim, venue allow-list, session id surrogate. Held
 * as {@link org.springframework.security.core.Authentication#getPrincipal()}. Defensive {@code
 * Set.copyOf} in the canonical constructor prevents post-construction mutation of the venue
 * allow-list (authorization state).
 *
 * <p>Implements {@link AuthenticatedPrincipal} and pins {@link #getName()} to the Firebase UID.
 * Spring Security's {@code UsernamePasswordAuthenticationToken.getName()} delegates to this; if
 * left unoverridden, it would fall through to the record's auto-generated {@code toString()} and
 * grow unboundedly with the venue allow-list. Mirrors the AsoviewClone commerce-core {@code
 * AuthenticatedUser.getName()} override that fixed a VARCHAR(128) overflow in JPA audit columns. No
 * active exploit in ticketing-service today (no {@code AuditorAware} wired), but the guard prevents
 * a recurrence if auditing is added later.
 */
public record ScannerPrincipal(
    String userId, String tenantId, Set<String> scannerVenues, String sessionId)
    implements AuthenticatedPrincipal {
  public ScannerPrincipal {
    scannerVenues = scannerVenues == null ? Set.of() : Set.copyOf(scannerVenues);
  }

  @Override
  public String getName() {
    return userId;
  }
}
