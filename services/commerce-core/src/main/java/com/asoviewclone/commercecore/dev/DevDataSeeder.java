package com.asoviewclone.commercecore.dev;

import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Populates Spanner inventory_slots for every seeded product variant so the local/dev backend can
 * answer {@code GET /v1/products/{id}/availability} with non-empty results. Runs only under the
 * {@code local} or {@code dev} profile and only after {@link
 * com.asoviewclone.commercecore.config.SpannerDdlBootstrap} has applied the DDL (enforced via
 * {@link Order}).
 *
 * <p>Idempotent: before inserting per-variant slots, probes with a single-row SELECT and skips if
 * any slot already exists for that variant. Safe to re-run.
 *
 * <p>CLAUDE.md SlotPicker.jst rule: slot dates and times are computed in {@code Asia/Tokyo}, never
 * UTC.
 */
@Component
@Profile({"local", "dev"})
@Order(100) // after SpannerDdlBootstrap (default order)
public class DevDataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
  private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
  private static final int DAYS = 30;
  private static final int CAPACITY = 8;

  private static final LocalTime[][] TIME_WINDOWS = {
    {LocalTime.of(10, 0), LocalTime.of(12, 0)},
    {LocalTime.of(13, 0), LocalTime.of(15, 0)},
    {LocalTime.of(16, 0), LocalTime.of(18, 0)},
  };

  private final ProductVariantRepository variantRepository;
  private final DatabaseClient databaseClient;

  public DevDataSeeder(ProductVariantRepository variantRepository, DatabaseClient databaseClient) {
    this.variantRepository = variantRepository;
    this.databaseClient = databaseClient;
  }

  @Override
  public void run(String... args) {
    List<ProductVariant> variants = variantRepository.findAll();
    if (variants.isEmpty()) {
      log.info("DevDataSeeder: no product variants found, skipping inventory seed");
      return;
    }

    int seededVariants = 0;
    int totalRows = 0;
    LocalDate startDate = LocalDate.now(JST);

    for (ProductVariant variant : variants) {
      String variantId = variant.getId().toString();
      if (hasExistingSlots(variantId)) {
        continue;
      }
      List<Mutation> mutations = new ArrayList<>(DAYS * TIME_WINDOWS.length);
      for (int d = 0; d < DAYS; d++) {
        LocalDate date = startDate.plusDays(d);
        String slotDate = date.toString(); // yyyy-MM-dd
        for (LocalTime[] window : TIME_WINDOWS) {
          String slotId = UUID.randomUUID().toString();
          mutations.add(
              Mutation.newInsertBuilder("inventory_slots")
                  .set("slot_id")
                  .to(slotId)
                  .set("product_variant_id")
                  .to(variantId)
                  .set("slot_date")
                  .to(slotDate)
                  .set("start_time")
                  .to(window[0].toString())
                  .set("end_time")
                  .to(window[1].toString())
                  .set("total_capacity")
                  .to((long) CAPACITY)
                  .set("reserved_count")
                  .to(0L)
                  .set("created_at")
                  .to(Value.COMMIT_TIMESTAMP)
                  .build());
        }
      }
      databaseClient.write(mutations);
      seededVariants++;
      totalRows += mutations.size();
    }

    log.info(
        "DevDataSeeder: seeded {} inventory_slots rows for {} variants ({} skipped as already seeded)",
        totalRows,
        seededVariants,
        variants.size() - seededVariants);
  }

  private boolean hasExistingSlots(String variantId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT slot_id FROM inventory_slots WHERE product_variant_id = @vid LIMIT 1")
            .bind("vid")
            .to(variantId)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      return rs.next();
    }
  }
}
