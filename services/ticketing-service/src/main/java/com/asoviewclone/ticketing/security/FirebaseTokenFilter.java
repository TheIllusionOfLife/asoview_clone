package com.asoviewclone.ticketing.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

  public FirebaseTokenFilter(FirebaseAuth firebaseAuth) {
    this.firebaseAuth = firebaseAuth;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri != null
        && (uri.equals("/healthz")
            || uri.startsWith("/actuator/health")
            || uri.startsWith("/actuator/info"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }
    String token = header.substring(7);
    try {
      // checkRevoked=true rejects tokens issued before FirebaseAuth.revokeRefreshTokens(uid).
      // This is Firebase's built-in session revocation mechanism; Firebase ID tokens do not
      // carry a `jti` claim, so the DB-backed revoked_sessions table cannot key by it.
      FirebaseToken decoded = firebaseAuth.verifyIdToken(token, true);

      String tenantId = (String) decoded.getClaims().get("tenantId");
      Set<String> scannerVenues = new HashSet<>();
      Object venuesClaim = decoded.getClaims().get("scannerVenues");
      if (venuesClaim instanceof Collection<?> col) {
        for (Object v : col) {
          if (v instanceof String s) {
            scannerVenues.add(s);
          }
        }
      }

      List<SimpleGrantedAuthority> authorities = new ArrayList<>();
      authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
      if (Boolean.TRUE.equals(decoded.getClaims().get("admin"))) {
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
      }
      Object rolesClaim = decoded.getClaims().get("roles");
      if (rolesClaim instanceof Collection<?> roles) {
        for (Object r : roles) {
          if ("SCANNER".equals(r)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SCANNER"));
          }
        }
      }

      // Session id surrogate: auth_time from the token. Stable across refresh within a session;
      // changes on re-login. Used only for audit correlation, not for authorization.
      Object authTime = decoded.getClaims().get("auth_time");
      String sessionId = authTime != null ? String.valueOf(authTime) : null;

      ScannerPrincipal principal =
          new ScannerPrincipal(decoded.getUid(), tenantId, scannerVenues, sessionId);
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(principal, null, authorities);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (com.google.firebase.auth.FirebaseAuthException e) {
      log.warn("Firebase token verification failed: {}", e.getMessage());
      writeUnauthorized(response, "Invalid token");
      return;
    } catch (Exception e) {
      throw new ServletException("Firebase auth error", e);
    }
    chain.doFilter(request, response);
  }

  private static void writeUnauthorized(HttpServletResponse response, String detail)
      throws IOException {
    response.setStatus(401);
    response.setContentType("application/json");
    // detail is a static string literal; JSON-safe. Do not pass dynamic content here.
    response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"detail\":\"" + detail + "\"}");
  }
}
