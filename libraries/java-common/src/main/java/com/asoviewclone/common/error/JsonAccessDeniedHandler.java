package com.asoviewclone.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Writes a JSON body on authenticated-but-unauthorized requests so direct Spring Security denials
 * use the same {@code {error, message, timestamp}} envelope as {@link GlobalExceptionHandler}.
 *
 * <p>Without this, {@code .anyRequest().authenticated()} returns Spring Security's default
 * empty-body 403. The validation-error-passthrough fix only covered the internal ERROR re-dispatch;
 * direct denials from the SecurityFilterChain go through {@code ExceptionTranslationFilter} and
 * never hit any {@code @ExceptionHandler}. The Next.js consumer would see an empty 403, conclude
 * the session is dead, and sign the user out — even when the request was merely lacking a role.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(JsonAccessDeniedHandler.class);

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    log.debug(
        "Access denied for {} {}: {}",
        request.getMethod(),
        request.getRequestURI(),
        ex.getMessage());
    ErrorResponseWriter.write(
        response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied");
  }
}
