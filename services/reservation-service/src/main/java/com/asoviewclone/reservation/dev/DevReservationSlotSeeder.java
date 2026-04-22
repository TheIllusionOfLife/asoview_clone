package com.asoviewclone.reservation.dev;

import com.asoviewclone.reservation.model.ReservationSlot;
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
import java.util.Set;
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
 * 16:00–18:00, capacity 8). Idempotent via a read-before-write skip keyed on {@code (venue_id,
 * slot_date, start_time)} — the repository's {@code create()} inserts a fresh {@code slot_id} every
 * time, so we must dedupe ourselves.
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
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DevReservationSlotSeeder(
      ReservationSlotRepository repository,
      @Value(
              "${demo.seed.commerce-core-base-url:http://commerce-core.core-services.svc.cluster.local:8080}")
          String commerceCoreBaseUrl) {
    this.repository = repository;
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
      int created = 0;
      int skipped = 0;
      for (Product product : products) {
        for (int dayOffset = 0; dayOffset < DAYS_AHEAD; dayOffset++) {
          String date = LocalDate.now().plusDays(dayOffset).toString();
          List<ReservationSlot> existing =
              repository.findByVenueAndDate(product.venueId, date, null);
          Set<String> existingStartTimes =
              existing.stream()
                  .map(ReservationSlot::startTime)
                  .collect(java.util.stream.Collectors.toUnmodifiableSet());
          for (int i = 0; i < START_TIMES.size(); i++) {
            String start = START_TIMES.get(i);
            if (existingStartTimes.contains(start)) {
              skipped++;
              continue;
            }
            repository.create(
                product.tenantId,
                product.venueId,
                product.id,
                date,
                start,
                END_TIMES.get(i),
                CAPACITY);
            created++;
          }
        }
      }
      log.info(
          "DevReservationSlotSeeder: seeded {} new slot(s) across {} product(s); {} already existed",
          created,
          products.size(),
          skipped);
    } catch (Exception e) {
      // Fail-open: a seeder crash must never block app startup. Log loudly so ops can spot it.
      log.error("DevReservationSlotSeeder: seeding failed — continuing startup", e);
    }
  }

  private List<Product> fetchProducts(int limit) throws Exception {
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(commerceCoreBaseUrl + "/v1/products?size=" + limit))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      throw new IllegalStateException(
          "commerce-core /v1/products returned " + res.statusCode() + ": " + res.body());
    }
    ProductPage page = objectMapper.readValue(res.body(), ProductPage.class);
    return page.content == null ? List.of() : page.content;
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
