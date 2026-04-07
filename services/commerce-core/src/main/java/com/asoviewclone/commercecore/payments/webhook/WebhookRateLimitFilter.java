package com.asoviewclone.commercecore.payments.webhook;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter applied to {@code /v1/payments/webhooks/**} that enforces two pre-auth guards:
 *
 * <ul>
 *   <li>An in-memory token bucket keyed on remote IP (default 60 req/min) so a single abusive
 *       caller cannot drown the signature-verification path.
 *   <li>A {@code Content-Length} cap (default 64KB) so we reject oversized payloads before reading
 *       the body into memory for signature verification.
 * </ul>
 *
 * <p>The filter runs BEFORE {@code FirebaseTokenFilter} (see {@code WebhookRateLimitConfig}) so
 * anonymous, unauthenticated webhook callers still get rate-limited. The in-memory map is
 * acceptable for phase 2: webhook traffic is low and a single commerce-core replica serves the
 * endpoint in staging/prod behind a load balancer with its own per-IP throttling. If we scale out
 * we will migrate to a Redis-backed Bucket4j proxy.
 */
@Component
public class WebhookRateLimitFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(WebhookRateLimitFilter.class);
  private static final String WEBHOOK_PATH_PREFIX = "/v1/payments/webhooks/";

  private final long maxBodyBytes;
  private final int requestsPerMinute;
  private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public WebhookRateLimitFilter(
      @Value("${app.payments.webhook.max-body-bytes:65536}") long maxBodyBytes,
      @Value("${app.payments.webhook.requests-per-minute:60}") int requestsPerMinute) {
    this.maxBodyBytes = maxBodyBytes;
    this.requestsPerMinute = requestsPerMinute;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return uri == null || !uri.startsWith(WEBHOOK_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long contentLength = request.getContentLengthLong();
    if (contentLength > maxBodyBytes) {
      log.warn(
          "Webhook body too large: content_length={} max={} remote={}",
          contentLength,
          maxBodyBytes,
          request.getRemoteAddr());
      response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
      return;
    }

    String key = clientKey(request);
    Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket());
    if (!bucket.tryConsume(1)) {
      log.warn("Webhook rate limit exceeded: remote={}", key);
      response.setStatus(429);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(requestsPerMinute)
            .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private static String clientKey(HttpServletRequest request) {
    String addr = request.getRemoteAddr();
    return addr != null ? addr : "unknown";
  }
}
