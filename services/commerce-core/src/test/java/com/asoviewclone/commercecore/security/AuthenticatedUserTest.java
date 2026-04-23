package com.asoviewclone.commercecore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import java.util.HashMap;
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

  // tenantRoles is defensively copied into an immutable map so a caller
  // cannot mutate the principal's authority set after construction.
  @Test
  void tenantRolesAreImmutable() {
    Map<UUID, TenantRole> mutable = new HashMap<>();
    mutable.put(UUID.randomUUID(), TenantRole.VIEWER);
    AuthenticatedUser user = new AuthenticatedUser("uid", "e@x", UUID.randomUUID(), mutable);

    mutable.put(UUID.randomUUID(), TenantRole.OWNER);

    assertThat(user.tenantRoles()).hasSize(1);
    assertThatThrownBy(() -> user.tenantRoles().put(UUID.randomUUID(), TenantRole.OWNER))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // Compact constructor must reject nulls so a future record-field addition
  // cannot silently revive SQLState 22001 (bloated toString spilling into
  // created_by VARCHAR(128)) via a principal built with missing identity.
  @Test
  void rejectsNullRequiredFields() {
    UUID userId = UUID.randomUUID();
    Map<UUID, TenantRole> roles = Map.of(UUID.randomUUID(), TenantRole.VIEWER);

    assertThatThrownBy(() -> new AuthenticatedUser(null, "e@x", userId, roles))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("firebaseUid");
    assertThatThrownBy(() -> new AuthenticatedUser("uid", null, userId, roles))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("email");
    assertThatThrownBy(() -> new AuthenticatedUser("uid", "e@x", null, roles))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("userId");
    assertThatThrownBy(() -> new AuthenticatedUser("uid", "e@x", userId, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tenantRoles");
  }
}
