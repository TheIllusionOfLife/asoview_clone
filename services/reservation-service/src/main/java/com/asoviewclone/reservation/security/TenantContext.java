package com.asoviewclone.reservation.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Extracts the authenticated user's tenantId from SecurityContextHolder. The tenantId is stored in
 * authentication details by FirebaseTokenFilter. Returns null if no tenantId claim is present
 * (global admin).
 */
public final class TenantContext {

  private TenantContext() {}

  public static String getCurrentTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof String tenantId) {
      return tenantId;
    }
    return null;
  }

  /**
   * Verify that the authenticated user's tenantId matches the resource's tenantId. Global admins
   * (no tenantId claim) are allowed to access all resources.
   *
   * @throws org.springframework.security.access.AccessDeniedException if tenantId mismatch
   */
  public static void requireTenant(String resourceTenantId) {
    String userTenantId = getCurrentTenantId();
    if (userTenantId != null && !userTenantId.equals(resourceTenantId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Tenant mismatch: user tenant " + userTenantId + " cannot access resource tenant "
              + resourceTenantId);
    }
  }
}
