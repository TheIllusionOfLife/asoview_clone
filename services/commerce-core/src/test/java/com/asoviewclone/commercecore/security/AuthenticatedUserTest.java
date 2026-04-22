package com.asoviewclone.commercecore.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AuthenticatedUserTest {

  // Spring Data JPA auditing writes Authentication.getName() into the
  // VARCHAR(128) created_by / updated_by columns. Before this class
  // implemented AuthenticatedPrincipal, getName() fell through to the
  // record's auto-generated toString() (~140 chars) and every audited
  // insert failed with SQLState 22001, which the payments service caught
  // as a DataIntegrityViolationException and mislabeled "already has a
  // payment in flight."
  @Test
  void getNameFitsAuditVarchar128() {
    AuthenticatedUser user =
        new AuthenticatedUser(
            "firebaseUidExample1234567890",
            "e2e-test-2@asoview-clone.dev",
            UUID.fromString("0223e2a8-5797-4172-9771-b9d796890c9d"),
            Map.of(UUID.randomUUID(), TenantRole.VIEWER));

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());

    assertThat(auth.getName()).hasSizeLessThanOrEqualTo(128);
    assertThat(auth.getName()).isEqualTo(user.userId().toString());
  }
}
