package com.asoviewclone.commercecore.entitlements.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.identity.model.Tenant;
import com.asoviewclone.commercecore.identity.model.Venue;
import com.asoviewclone.commercecore.identity.repository.TenantRepository;
import com.asoviewclone.commercecore.identity.repository.VenueRepository;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Backfill job validation. Seeds a mix of resolvable-NULL, already-backfilled, and unresolvable
 * (orphaned variant) ticket_passes, runs the job, and asserts that only the resolvable NULLs are
 * written, already-backfilled rows are untouched, and unresolvable rows are surfaced as errors.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
@TestPropertySource(properties = "commerce.ticket-pass-backfill.enabled=true")
class TicketPassBackfillJobTest {

  @Autowired private TicketPassBackfillJob job;
  @Autowired private DatabaseClient spannerClient;
  @Autowired private ProductRepository productRepository;
  @Autowired private ProductVariantRepository productVariantRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private VenueRepository venueRepository;

  @BeforeEach
  void purgeSpannerState() {
    // @SpringBootTest reuses the Spanner emulator container across test methods; purge
    // ticket_passes + entitlements between tests so one test's orphan row doesn't contaminate
    // the next test's assertions. Children must be deleted before parent (entitlements is the
    // FK target for ticket_passes via entitlement_id).
    spannerClient.write(
        java.util.List.of(
            com.google.cloud.spanner.Mutation.delete("ticket_passes", KeySet.all()),
            com.google.cloud.spanner.Mutation.delete("entitlements", KeySet.all())));
  }

  @Test
  void run_populatesNullRows_leavesAlreadyBackfilledAlone_reportsUnresolvable() {
    Fixture f = seedFixture("fx-a");

    // 3 resolvable-NULL passes (pre-V6 style).
    for (int i = 0; i < 3; i++) {
      seedPass(UUID.randomUUID().toString(), f.variantId, null, null);
    }
    // 2 already-backfilled passes with a DIFFERENT venue/tenant to prove they're untouched.
    String knownVenue = UUID.randomUUID().toString();
    String knownTenant = UUID.randomUUID().toString();
    for (int i = 0; i < 2; i++) {
      seedPass(UUID.randomUUID().toString(), f.variantId, knownVenue, knownTenant);
    }
    // 1 unresolvable pass: variant UUID not present in Postgres.
    String orphanPassId = UUID.randomUUID().toString();
    seedPass(orphanPassId, UUID.randomUUID().toString(), null, null);

    TicketPassBackfillJob.Report report = job.run();

    assertThat(report.updated()).isEqualTo(3);
    assertThat(report.unresolved()).isEqualTo(1);
    assertThat(report.unresolvedPassIds()).containsExactly(orphanPassId);

    assertThat(countPassesWithVenue(f.variantId, f.venueId, f.tenantId)).isEqualTo(3);
    assertThat(countPassesWithVenue(f.variantId, knownVenue, knownTenant)).isEqualTo(2);
    assertThat(passVenueId(orphanPassId)).isNull();
  }

  @Test
  void run_isIdempotent_secondRunTouchesNothing() {
    Fixture f = seedFixture("fx-b");
    seedPass(UUID.randomUUID().toString(), f.variantId, null, null);

    TicketPassBackfillJob.Report first = job.run();
    TicketPassBackfillJob.Report second = job.run();

    assertThat(first.updated()).isEqualTo(1);
    assertThat(second.updated()).isEqualTo(0);
    assertThat(second.unresolved()).isEqualTo(0);
  }

  // --- fixture helpers ---

  private record Fixture(String tenantId, String venueId, String variantId) {}

  private Fixture seedFixture(String slugPrefix) {
    Tenant tenant = tenantRepository.save(new Tenant("Fixture " + slugPrefix, slugPrefix));
    Venue venue = venueRepository.save(new Venue(tenant.getId(), "Venue", "addr", 35.0, 139.0));
    Product product =
        new Product(
            tenant.getId(),
            venue.getId(),
            null,
            "Backfill Fixture Product",
            "desc",
            null,
            ProductStatus.ACTIVE);
    productRepository.save(product);
    ProductVariant variant =
        new ProductVariant(product, "Adult", new BigDecimal("1000"), "JPY", 60, 10);
    productVariantRepository.save(variant);
    return new Fixture(
        tenant.getId().toString(), venue.getId().toString(), variant.getId().toString());
  }

  private void seedPass(String passId, String variantId, String venueId, String tenantId) {
    String entitlementId = UUID.randomUUID().toString();
    Timestamp now = Timestamp.now();
    spannerClient.write(
        java.util.List.of(
            Mutation.newInsertBuilder("entitlements")
                .set("entitlement_id")
                .to(entitlementId)
                .set("order_id")
                .to(UUID.randomUUID().toString())
                .set("order_item_id")
                .to(UUID.randomUUID().toString())
                .set("user_id")
                .to(UUID.randomUUID().toString())
                .set("product_variant_id")
                .to(variantId)
                .set("type")
                .to("TICKET")
                .set("status")
                .to("ACTIVE")
                .set("created_at")
                .to(now)
                .build(),
            buildPassMutation(passId, entitlementId, venueId, tenantId, now)));
  }

  private Mutation buildPassMutation(
      String passId, String entitlementId, String venueId, String tenantId, Timestamp now) {
    Mutation.WriteBuilder b =
        Mutation.newInsertBuilder("ticket_passes")
            .set("ticket_pass_id")
            .to(passId)
            .set("entitlement_id")
            .to(entitlementId)
            .set("qr_code_payload")
            .to("TKT-" + passId.replace("-", "").substring(0, 16))
            .set("status")
            .to("VALID")
            .set("created_at")
            .to(now);
    if (venueId != null) {
      b.set("venue_id").to(venueId);
    }
    if (tenantId != null) {
      b.set("tenant_id").to(tenantId);
    }
    return b.build();
  }

  private long countPassesWithVenue(String variantIdHint, String venueId, String tenantId) {
    try (ResultSet rs =
        spannerClient
            .singleUse()
            .executeQuery(
                Statement.newBuilder(
                        "SELECT COUNT(*) AS c FROM ticket_passes tp "
                            + "JOIN entitlements e ON e.entitlement_id = tp.entitlement_id "
                            + "WHERE e.product_variant_id = @v AND tp.venue_id = @ven AND tp.tenant_id = @t")
                    .bind("v")
                    .to(variantIdHint)
                    .bind("ven")
                    .to(venueId)
                    .bind("t")
                    .to(tenantId)
                    .build())) {
      return rs.next() ? rs.getLong("c") : 0L;
    }
  }

  private String passVenueId(String passId) {
    try (ResultSet rs =
        spannerClient
            .singleUse()
            .executeQuery(
                Statement.newBuilder(
                        "SELECT venue_id FROM ticket_passes WHERE ticket_pass_id = @id")
                    .bind("id")
                    .to(passId)
                    .build())) {
      if (!rs.next()) {
        return null;
      }
      return rs.isNull("venue_id") ? null : rs.getString("venue_id");
    }
  }
}
