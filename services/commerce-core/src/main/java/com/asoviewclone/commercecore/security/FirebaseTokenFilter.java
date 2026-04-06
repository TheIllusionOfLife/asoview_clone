package com.asoviewclone.commercecore.security;

import com.asoviewclone.commercecore.identity.model.TenantRole;
import com.asoviewclone.commercecore.identity.model.TenantUser;
import com.asoviewclone.commercecore.identity.model.User;
import com.asoviewclone.commercecore.identity.repository.TenantUserRepository;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(FirebaseTokenFilter.class);

  private final FirebaseAuth firebaseAuth;
  private final UserRepository userRepository;
  private final TenantUserRepository tenantUserRepository;

  /**
   * Webhook ingress paths authenticate by provider signature verification inside the handler, not
   * by Firebase. Short-circuit the filter here so a stray {@code Authorization} header on a
   * provider-originated request cannot fail verification and return 401 before the webhook handler
   * ever runs.
   */
  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null && uri.startsWith("/v1/payments/webhooks/");
  }

  public FirebaseTokenFilter(
      FirebaseAuth firebaseAuth,
      UserRepository userRepository,
      TenantUserRepository tenantUserRepository) {
    this.firebaseAuth = firebaseAuth;
    this.userRepository = userRepository;
    this.tenantUserRepository = tenantUserRepository;
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
      FirebaseToken decodedToken = firebaseAuth.verifyIdToken(token);
      String uid = decodedToken.getUid();
      String email = decodedToken.getEmail();

      User user =
          userRepository
              .findByFirebaseUid(uid)
              .orElseGet(() -> userRepository.save(new User(uid, email, decodedToken.getName())));

      Map<UUID, TenantRole> tenantRoles = new HashMap<>();
      List<TenantUser> tenantUsers = tenantUserRepository.findByUserId(user.getId());
      for (TenantUser tu : tenantUsers) {
        tenantRoles.put(tu.getTenantId(), tu.getRole());
      }

      AuthenticatedUser authenticatedUser =
          new AuthenticatedUser(uid, email, user.getId(), tenantRoles);

      List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
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
