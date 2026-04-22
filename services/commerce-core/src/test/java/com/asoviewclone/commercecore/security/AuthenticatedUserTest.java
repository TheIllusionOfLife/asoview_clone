package com.asoviewclone.commercecore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
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

  // The fix relies on userId.toString() in getName(). A null userId would
  // either NPE or surface the string "null" into the audit column, so the
  // record's compact constructor rejects null required fields up front.
  @Test
  void rejectsNullRequiredFields() {
    assertThatNullPointerException()
        .isThrownBy(() -> new AuthenticatedUser(null, "e@x", UUID.randomUUID(), Map.of()));
    assertThatNullPointerException()
        .isThrownBy(() -> new AuthenticatedUser("uid", null, UUID.randomUUID(), Map.of()));
    assertThatNullPointerException()
        .isThrownBy(() -> new AuthenticatedUser("uid", "e@x", null, Map.of()));
    assertThatThrownBy(() -> new AuthenticatedUser("uid", "e@x", UUID.randomUUID(), null))
        .isInstanceOf(NullPointerException.class);
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
}
