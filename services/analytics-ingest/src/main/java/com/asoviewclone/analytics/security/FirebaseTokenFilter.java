package com.asoviewclone.analytics.security;

import com.google.firebase.auth.FirebaseAuth;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simplified Firebase token filter for analytics-ingest. Verifies the Firebase ID token but does
 * not perform user/tenant DB lookups (analytics-ingest has no user database).
 */
@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(FirebaseTokenFilter.class);

  private final FirebaseAuth firebaseAuth;

  public FirebaseTokenFilter(FirebaseAuth firebaseAuth) {
    this.firebaseAuth = firebaseAuth;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String token = authHeader.substring(7);
    try {
      var decodedToken = firebaseAuth.verifyIdToken(token);
      var authentication =
          new UsernamePasswordAuthenticationToken(
              decodedToken.getUid(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (Exception e) {
      log.warn("Firebase token verification failed: {}", e.getMessage());
      response.setStatus(401);
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid token\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
