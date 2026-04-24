package com.asoviewclone.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Writes a JSON body when an unauthenticated request hits a protected endpoint. Companion to {@link
 * JsonAccessDeniedHandler}; both produce the same {@code {error, message, timestamp}} envelope so
 * frontend branching on status alone is sufficient.
 *
 * <p>Default Spring Security behaviour is to emit an empty 401 with a {@code WWW-Authenticate:
 * Basic} header — meaningless for a Firebase-authenticated API and leaks Basic-auth affordance we
 * don't support. This implementation emits {@code 401 + JSON} without the header.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(JsonAuthenticationEntryPoint.class);

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    log.debug(
        "Unauthenticated request to {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.getMessage());
    // RFC 9110 §11.6.1 requires at least one WWW-Authenticate challenge on a
    // 401. We use `Bearer` without a realm because the tokens are Firebase
    // ID tokens; we deliberately omit the `realm` parameter (browsers will
    // not prompt for Basic credentials since the scheme is Bearer).
    response.setHeader("WWW-Authenticate", "Bearer");
    ErrorResponseWriter.write(
        response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }
}
