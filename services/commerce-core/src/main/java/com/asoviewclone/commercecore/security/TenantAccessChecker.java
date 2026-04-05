package com.asoviewclone.commercecore.security;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class TenantAccessChecker {

  public AuthenticatedUser getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser)) {
      throw new AccessDeniedException("No authenticated user");
    }
    return (AuthenticatedUser) auth.getPrincipal();
  }

  public void requireTenantRole(UUID tenantId, TenantRole requiredRole) {
    AuthenticatedUser user = getCurrentUser();
    if (!user.hasTenantRole(tenantId, requiredRole)) {
      throw new AccessDeniedException(
          "User does not have " + requiredRole + " role on tenant " + tenantId);
    }
  }
}
