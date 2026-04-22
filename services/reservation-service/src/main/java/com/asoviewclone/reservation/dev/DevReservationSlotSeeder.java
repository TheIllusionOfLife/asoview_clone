package com.asoviewclone.reservation.dev;

import com.asoviewclone.reservation.repository.ReservationSlotRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds {@code reservation_slots} on dev so the 60-second demo video can show a populated {@code
 * /me/reservations} page without operator/tenant-admin JWT. Gated behind {@code @Profile("dev")}
 * AND a {@code demo.seed.enabled} property so prod and staging environments are untouched by
 * default.
 *
 * <p>Strategy: on startup, fetch the first few products from commerce-core's public product list,
 * then for each product generate 14 days of three 2-hour slots (10:00–12:00, 13:00–15:00,
 * 16:00–18:00, capacity 8). Atomic check-and-insert via {@link
 * ReservationSlotRepository#createIfAbsent} — Spanner read-write transaction semantics make the
 * seeder safe under concurrent replica startup without requiring a new UNIQUE INDEX DDL.
 *
 * <p>CLAUDE.md self-call rule is moot here — the seeder does not publish any
 * {@code @TransactionalEventListener(AFTER_COMMIT)} events.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "demo.seed.enabled", havingValue = "true")
@Order(200)
public class DevReservationSlotSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DevReservationSlotSeeder.class);

  private static final List<String> START_TIMES = List.of("10:00", "13:00", "16:00");
  private static final List<String> END_TIMES = List.of("12:00", "15:00", "18:00");
  private static final int DAYS_AHEAD = 14;
  private static final int PRODUCTS_TO_SEED = 3;
  private static final long CAPACITY = 8L;

  private final ReservationSlotRepository repository;
  private final String commerceCoreBaseUrl;
  private final ObjectMapper objectMapper;

  public DevReservationSlotSeeder(
      ReservationSlotRepository repository,
      // Spring-managed mapper — inherits any app-wide @JsonNaming / @JsonIgnoreProperties
      // policies and keeps ObjectMapper instances consolidated.
      ObjectMapper objectMapper,
      @Value(
              "${demo.seed.commerce-core-base-url:http://commerce-core.core-services.svc.cluster.local:8080}")
          String commerceCoreBaseUrl) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.commerceCoreBaseUrl = commerceCoreBaseUrl;
  }

  @Override
  public void run(String... args) {
    try {
      List<Product> products = fetchProducts(PRODUCTS_TO_SEED);
      if (products.isEmpty()) {
        log.warn("DevReservationSlotSeeder: no products returned — skipping");
        return;
      }
      // Pin the calendar window once so a midnight-boundary run cannot shift
      // `today` mid-loop and produce a jagged 14-day window.
      LocalDate today = LocalDate.now();
      int created = 0;
      int skipped = 0;
      int droppedInvalid = 0;
      for (Product product : products) {
        if (isNullOrBlank(product.id)
            || isNullOrBlank(product.tenantId)
            || isNullOrBlank(product.venueId)) {
          // commerce-core should never return a partial product on the dev
          // seed path, but guard against an NPE that the outer catch would
          // otherwise swallow silently.
          droppedInvalid++;
          log.warn(
              "DevReservationSlotSeeder: skipping product with null/blank id/tenant/venue: id={} tenantId={} venueId={}",
              product.id,
              product.tenantId,
              product.venueId);
          continue;
        }
        for (int dayOffset = 0; dayOffset < DAYS_AHEAD; dayOffset++) {
          String date = today.plusDays(dayOffset).toString();
          for (int i = 0; i < START_TIMES.size(); i++) {
            // Atomic check-and-insert: createIfAbsent runs inside a Spanner
            // read-write transaction, so concurrent pods racing through
            // startup cannot both insert duplicate rows — one of the
            // transactions ABORTS and retries, observing the now-inserted
            // row on the retry. Matches the CLAUDE.md INSERT-FIRST
            // idempotency rule without requiring a new DDL.
            boolean inserted =
                repository
                    .createIfAbsent(
                        product.tenantId,
                        product.venueId,
                        product.id,
                        date,
                        START_TIMES.get(i),
                        END_TIMES.get(i),
                        CAPACITY)
                    .isPresent();
            if (inserted) {
              created++;
            } else {
              skipped++;
            }
          }
        }
      }
      log.info(
          "DevReservationSlotSeeder: seeded {} new slot(s) across {} product(s); {} already existed; {} invalid products skipped",
          created,
          products.size() - droppedInvalid,
          skipped,
          droppedInvalid);
    } catch (InterruptedException ie) {
      // Re-raise the interrupt so JVM shutdown is not delayed by a sleep
      // still in flight deeper in the stack.
      Thread.currentThread().interrupt();
      log.warn("DevReservationSlotSeeder: seeding interrupted — continuing startup");
    } catch (Exception e) {
      // Fail-open: a seeder crash must never block app startup. Log loudly so ops can spot it.
      log.error("DevReservationSlotSeeder: seeding failed — continuing startup", e);
    }
  }

  private static boolean isNullOrBlank(String s) {
    return s == null || s.isBlank();
  }

  private List<Product> fetchProducts(int limit) throws Exception {
    // HttpClient is AutoCloseable in Java 21 and owns a selector thread + a
    // connection pool. Instantiate locally in try-with-resources so the
    // seeder (which only runs once at startup) does not leave idle threads
    // parked for the app's whole lifetime.
    try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(commerceCoreBaseUrl + "/v1/products?size=" + limit))
              .timeout(Duration.ofSeconds(10))
              .GET()
              .build();
      Exception lastError = null;
      long backoffMs = 1000L;
      for (int attempt = 1; attempt <= 3; attempt++) {
        try {
          HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
          if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                "commerce-core /v1/products returned " + res.statusCode() + ": " + res.body());
          }
          ProductPage page = objectMapper.readValue(res.body(), ProductPage.class);
          return page.content == null ? List.of() : page.content;
        } catch (InterruptedException ie) {
          // Preserve interrupt status so SIGTERM during startup doesn't
          // block shutdown. Abort retrying.
          Thread.currentThread().interrupt();
          log.warn("DevReservationSlotSeeder: interrupted mid-HTTP; aborting retries");
          throw ie;
        } catch (Exception e) {
          lastError = e;
          if (attempt < 3) {
            log.warn(
                "DevReservationSlotSeeder: fetchProducts attempt {} failed ({}); retrying in {}ms",
                attempt,
                e.getMessage(),
                backoffMs);
            try {
              Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              throw ie;
            }
            backoffMs *= 2;
          }
        }
      }
      throw new IllegalStateException("fetchProducts failed after 3 attempts", lastError);
    }
  }

  // DTOs sized to the subset we care about; Jackson ignores everything else.
  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class ProductPage {
    public List<Product> content;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class Product {
    public String id;

    @JsonProperty("tenantId")
    public String tenantId;

    @JsonProperty("venueId")
    public String venueId;
  }
}
