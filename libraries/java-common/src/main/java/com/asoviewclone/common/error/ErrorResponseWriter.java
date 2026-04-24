package com.asoviewclone.common.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;

/**
 * Shared emitter for the {@code {error, message, timestamp}} JSON envelope used by {@link
 * JsonAccessDeniedHandler} and {@link JsonAuthenticationEntryPoint}. Hand-rolled to avoid dragging
 * Jackson or an injectable {@code ObjectMapper} into the java-common library — both handlers run
 * before the DispatcherServlet is engaged, so there's no guarantee an {@code ObjectMapper} bean is
 * available (the servlet container is still spinning up filter chains).
 */
final class ErrorResponseWriter {

  private ErrorResponseWriter() {}

  static void write(HttpServletResponse response, int status, String errorCode, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String body =
        "{\"error\":\""
            + escapeJson(errorCode)
            + "\",\"message\":\""
            + escapeJson(message)
            + "\",\"timestamp\":\""
            + Instant.now()
            + "\"}";
    response.getWriter().write(body);
  }

  private static String escapeJson(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length() + 2);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
