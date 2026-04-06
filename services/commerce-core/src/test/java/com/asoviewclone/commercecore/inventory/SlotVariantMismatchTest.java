package com.asoviewclone.commercecore.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.commercecore.catalog.model.Category;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.CategoryRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.identity.model.Tenant;
import com.asoviewclone.commercecore.identity.model.Venue;
import com.asoviewclone.commercecore.identity.repository.TenantRepository;
import com.asoviewclone.commercecore.identity.repository.VenueRepository;
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.asoviewclone.common.error.ValidationException;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Validates that createOrder rejects an order where the slot belongs to a different product
 * variant than the one requested, and that no hold rows remain for that slot after rejection.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class SlotVariantMismatchTest {

  @Autowired private OrderService orderService;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private ProductVariantRepository productVariantRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private VenueRepository venueRepository;
  @Autowired private DatabaseClient spannerClient;

  private String variantAId;
  private String variantBId;
  private String slotIdForVariantA;

  @BeforeEach
  void setUp() {
    Tenant tenant =
        tenantRepository.save(new Tenant("Mismatch Op", "mismatch-op-" + UUID.randomUUID()));
    Venue venue =
        venueRepository.save(new Venue(tenant.getId(), "Mismatch Venue", "Tokyo", 35.6, 139.7));
    Category category =
        categoryRepository.save(
            new Category(
                "MismatchCat-" + UUID.randomUUID(),
                "mismatch-" + UUID.randomUUID(),
                null,
                1,
                null));
    Product product =
        productRepository.save(
            new Product(
                tenant.getId(),
                venue.getId(),
                category.getId(),
                "Mismatch Product",
                "desc",
                null,
                ProductStatus.ACTIVE));
    ProductVariant variantA =
        productVariantRepository.save(
            new ProductVariant(product, "Adult", new BigDecimal("5000"), "JPY", 60, 10));
    ProductVariant variantB =
        productVariantRepository.save(
            new ProductVariant(product, "Child", new BigDecimal("3000"), "JPY", 60, 10));
    variantAId = variantA.getId().toString();
    variantBId = variantB.getId().toString();

    slotIdForVariantA = UUID.randomUUID().toString();
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("inventory_slots")
                .set("slot_id")
                .to(slotIdForVariantA)
                .set("product_variant_id")
                .to(variantAId)
                .set("slot_date")
                .to("2026-09-01")
                .set("start_time")
                .to("10:00")
                .set("end_time")
                .to("11:00")
                .set("total_capacity")
                .to(5)
                .set("reserved_count")
                .to(0)
                .set("created_at")
                .to(Timestamp.now())
                .build()));
  }

  @Test
  void createOrderWithMismatchedSlotAndVariantIsRejectedAndReleasesHold() {
    String userId = UUID.randomUUID().toString();

    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    userId,
                    UUID.randomUUID().toString(),
                    List.of(
                        new OrderService.CreateOrderItemRequest(
                            variantBId, slotIdForVariantA, 1))))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Slot does not belong to requested product variant");

    // Verify there are no inventory_holds rows for the slot: the mismatch path should release
    // any hold it created.
    long holdCount = countHoldsForSlot(slotIdForVariantA);
    assertThat(holdCount).isZero();
  }

  private long countHoldsForSlot(String slotId) {
    Statement stmt =
        Statement.newBuilder("SELECT COUNT(*) AS c FROM inventory_holds WHERE slot_id = @s")
            .bind("s")
            .to(slotId)
            .build();
    try (ResultSet rs = spannerClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getLong("c");
      }
    }
    return 0;
  }
}
