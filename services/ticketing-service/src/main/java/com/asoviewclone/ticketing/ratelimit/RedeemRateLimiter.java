package com.asoviewclone.ticketing.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory per-process rate limiter for the redeem endpoint. Simple token-bucket approximation:
 * each key keeps last-window timestamps and request counts. For single-pod ticketing-service this
 * is sufficient; scale-out needs Redis-backed Bucket4j.
 */
@Component
public class RedeemRateLimiter {

  private final int perScannerRps;
  private final int perScannerBurst;
  private final int perPassPerMinute;
  private final int perIpRps;

  private final Map<String, Bucket> scannerBuckets = new ConcurrentHashMap<>();
  private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
  private final Map<String, Bucket> passBuckets = new ConcurrentHashMap<>();

  public RedeemRateLimiter(
      @Value("${ticketing.redeem.rate-limit.per-scanner-rps:20}") int perScannerRps,
      @Value("${ticketing.redeem.rate-limit.per-scanner-burst:50}") int perScannerBurst,
      @Value("${ticketing.redeem.rate-limit.per-pass-attempts-per-minute:5}") int perPassPerMinute,
      @Value("${ticketing.redeem.rate-limit.per-ip-rps:50}") int perIpRps) {
    this.perScannerRps = perScannerRps;
    this.perScannerBurst = perScannerBurst;
    this.perPassPerMinute = perPassPerMinute;
    this.perIpRps = perIpRps;
  }

  public boolean tryAcquire(String scannerUserId, String sourceIp, String passId) {
    long now = System.currentTimeMillis();
    if (scannerUserId != null
        && !scannerBuckets
            .computeIfAbsent(scannerUserId, k -> new Bucket(perScannerRps * 1000L, perScannerBurst))
            .allow(now)) {
      return false;
    }
    if (sourceIp != null
        && !ipBuckets
            .computeIfAbsent(sourceIp, k -> new Bucket(perIpRps * 1000L, perIpRps * 2))
            .allow(now)) {
      return false;
    }
    if (passId != null
        && !passBuckets
            .computeIfAbsent(passId, k -> new Bucket(60_000L, perPassPerMinute))
            .allow(now)) {
      return false;
    }
    return true;
  }

  /**
   * Token bucket: capacity tokens, refilled proportionally over {@code windowMillis}. allow()
   * consumes one token; returns false when empty.
   */
  private static final class Bucket {
    private final long windowMillis;
    private final int capacity;
    private double tokens;
    private long lastRefill;

    Bucket(long windowMillis, int capacity) {
      this.windowMillis = windowMillis;
      this.capacity = capacity;
      this.tokens = capacity;
      this.lastRefill = System.currentTimeMillis();
    }

    synchronized boolean allow(long now) {
      long elapsed = now - lastRefill;
      if (elapsed > 0) {
        tokens = Math.min(capacity, tokens + (double) elapsed * capacity / windowMillis);
        lastRefill = now;
      }
      if (tokens >= 1) {
        tokens -= 1;
        return true;
      }
      return false;
    }
  }
}
