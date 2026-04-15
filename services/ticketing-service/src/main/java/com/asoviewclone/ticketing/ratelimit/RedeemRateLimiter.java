package com.asoviewclone.ticketing.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory per-process rate limiter for the redeem endpoint. Token buckets keyed on scanner, IP,
 * and pass id are held in bounded Caffeine caches (maximumSize + expireAfterAccess) per CLAUDE.md
 * rule for high-cardinality external keys — unbounded maps would be a DoS vector. Scale-out past
 * one pod needs a Redis-backed Bucket4j.
 */
@Component
public class RedeemRateLimiter {

  private static final long MAX_KEYS = 10_000L;
  private static final Duration EVICT_AFTER = Duration.ofMinutes(10);

  private final int perScannerRps;
  private final int perScannerBurst;
  private final int perPassPerMinute;
  private final int perIpRps;
  private final int perIpBurst;

  private final Cache<String, Bucket> scannerBuckets;
  private final Cache<String, Bucket> ipBuckets;
  private final Cache<String, Bucket> passBuckets;

  public RedeemRateLimiter(
      @Value("${ticketing.redeem.rate-limit.per-scanner-rps:20}") int perScannerRps,
      @Value("${ticketing.redeem.rate-limit.per-scanner-burst:50}") int perScannerBurst,
      @Value("${ticketing.redeem.rate-limit.per-pass-attempts-per-minute:5}") int perPassPerMinute,
      @Value("${ticketing.redeem.rate-limit.per-ip-rps:50}") int perIpRps) {
    this.perScannerRps = perScannerRps;
    this.perScannerBurst = perScannerBurst;
    this.perPassPerMinute = perPassPerMinute;
    this.perIpRps = perIpRps;
    this.perIpBurst = Math.max(perIpRps * 2, perIpRps);
    this.scannerBuckets =
        Caffeine.newBuilder().maximumSize(MAX_KEYS).expireAfterAccess(EVICT_AFTER).build();
    this.ipBuckets =
        Caffeine.newBuilder().maximumSize(MAX_KEYS).expireAfterAccess(EVICT_AFTER).build();
    this.passBuckets =
        Caffeine.newBuilder().maximumSize(MAX_KEYS).expireAfterAccess(EVICT_AFTER).build();
  }

  public boolean tryAcquire(String scannerUserId, String sourceIp, String passId) {
    long now = System.currentTimeMillis();
    if (scannerUserId != null
        && !scannerBuckets
            .get(scannerUserId, k -> newRpsBucket(perScannerRps, perScannerBurst))
            .allow(now)) {
      return false;
    }
    if (sourceIp != null
        && !ipBuckets.get(sourceIp, k -> newRpsBucket(perIpRps, perIpBurst)).allow(now)) {
      return false;
    }
    if (passId != null
        && !passBuckets.get(passId, k -> new Bucket(60_000L, perPassPerMinute)).allow(now)) {
      return false;
    }
    return true;
  }

  /**
   * Build a bucket that sustains {@code rps} and tolerates a {@code burst} spike. Refill rate is
   * burst/windowMillis tokens per ms; setting windowMillis = burst*1000/rps gives the desired
   * sustained rate. (The original rps*1000 formula produced rates 10-20x stricter than intended.)
   */
  private static Bucket newRpsBucket(int rps, int burst) {
    long windowMillis = Math.max(1, (long) burst * 1000L / Math.max(1, rps));
    return new Bucket(windowMillis, burst);
  }

  /**
   * Token bucket: capacity tokens, refilled proportionally over {@code windowMillis}. allow()
   * consumes one token; returns false when empty.
   */
  static final class Bucket {
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
