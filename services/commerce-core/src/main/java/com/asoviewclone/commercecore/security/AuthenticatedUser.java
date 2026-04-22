package com.asoviewclone.commercecore.security;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

public record AuthenticatedUser(
    String firebaseUid, String email, UUID userId, Map<UUID, TenantRole> tenantRoles)
    implements AuthenticatedPrincipal {

  /**
   * Spring Security's {@code UsernamePasswordAuthenticationToken.getName()} delegates to {@code
   * AuthenticatedPrincipal.getName()} before falling back to {@code toString()}. Without this
   * override, {@code getName()} returned the record's auto-generated {@code toString()} (~140 chars
   * including the tenantRoles map), which overflowed {@code created_by VARCHAR(128)} in the Spring
   * Data JPA auditor writes and masqueraded as a unique-constraint violation.
   */
  @Override
  public String getName() {
    return userId.toString();
  }

  public boolean hasTenantRole(UUID tenantId, TenantRole requiredRole) {
    TenantRole role = tenantRoles.get(tenantId);
    return role != null && role.getPrivilegeLevel() <= requiredRole.getPrivilegeLevel();
  }
}
