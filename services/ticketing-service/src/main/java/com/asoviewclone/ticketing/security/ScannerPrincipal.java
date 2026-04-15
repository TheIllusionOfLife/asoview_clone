package com.asoviewclone.ticketing.security;

import java.util.Set;

/**
 * Authenticated operator: Firebase UID, tenant claim, venue allow-list, session id (jti). Held as
 * {@link org.springframework.security.core.Authentication#getPrincipal()}.
 */
public record ScannerPrincipal(
    String userId, String tenantId, Set<String> scannerVenues, String sessionId) {}
