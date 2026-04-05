package com.asoviewclone.commercecore.security;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import java.util.Map;
import java.util.UUID;

public record AuthenticatedUser(
    String firebaseUid, String email, UUID userId, Map<UUID, TenantRole> tenantRoles) {

  public boolean hasTenantRole(UUID tenantId, TenantRole requiredRole) {
    TenantRole role = tenantRoles.get(tenantId);
    return role != null && role.ordinal() <= requiredRole.ordinal();
  }
}
