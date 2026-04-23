package com.asoviewclone.ticketing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ScannerPrincipalTest {

  // Pin getName() to the Firebase UID so any future JPA auditing wired into
  // ticketing-service cannot overflow created_by/updated_by VARCHAR(128). The
  // record's auto-generated toString() grows with the venue allow-list and
  // would otherwise be the default source for Authentication.getName().
  @Test
  void getNameFitsAuditVarchar128() {
    ScannerPrincipal principal =
        new ScannerPrincipal(
            "firebaseUidExample1234567890",
            "tenant-abc",
            Set.of("venue-1", "venue-2", "venue-3"),
            "session-xyz");

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());

    assertThat(auth.getName()).hasSizeLessThanOrEqualTo(128);
    assertThat(auth.getName()).isEqualTo(principal.userId());
  }

  // scannerVenues must be defensively copied so a caller cannot mutate the
  // venue allow-list post-construction.
  @Test
  void scannerVenuesAreImmutable() {
    java.util.Set<String> mutable = new java.util.HashSet<>();
    mutable.add("venue-1");
    ScannerPrincipal principal = new ScannerPrincipal("uid", "tenant-abc", mutable, "session-xyz");

    mutable.add("venue-2");

    assertThat(principal.scannerVenues()).hasSize(1);
  }
}
