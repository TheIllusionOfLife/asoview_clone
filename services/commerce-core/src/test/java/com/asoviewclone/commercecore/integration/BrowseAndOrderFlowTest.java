package com.asoviewclone.commercecore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.catalog.model.Category;
import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductStatus;
import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.CategoryRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductRepository;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.catalog.service.CatalogService;
import com.asoviewclone.commercecore.entitlements.model.Entitlement;
import com.asoviewclone.commercecore.entitlements.model.TicketPass;
import com.asoviewclone.commercecore.entitlements.repository.EntitlementRepository;
import com.asoviewclone.commercecore.entitlements.service.EntitlementServiceImpl;
import com.asoviewclone.commercecore.identity.model.Tenant;
import com.asoviewclone.commercecore.identity.model.Venue;
import com.asoviewclone.commercecore.identity.repository.TenantRepository;
import com.asoviewclone.commercecore.identity.repository.VenueRepository;
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.orders.service.OrderService.CreateOrderItemRequest;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.service.PaymentService;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class BrowseAndOrderFlowTest {

  @Autowired private CatalogService catalogService;
  @Autowired private OrderService orderService;
  @Autowired private PaymentService paymentService;
  @Autowired private InventoryService inventoryService;
  @Autowired private EntitlementServiceImpl entitlementService;
  @Autowired private EntitlementRepository entitlementRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private ProductVariantRepository productVariantRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private VenueRepository venueRepository;
  @Autowired private DatabaseClient spannerClient;

  private String variantId;
  private String slotId;
  private final String userId = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    // Seed Cloud SQL data
    Tenant tenant = tenantRepository.save(new Tenant("Test Operator", "test-op"));
    Venue venue =
        venueRepository.save(new Venue(tenant.getId(), "Test Venue", "Tokyo", 35.6, 139.7));
    Category category =
        categoryRepository.save(new Category("Adventure", "adventure", null, 1, null));
    Product product =
        productRepository.save(
            new Product(
                tenant.getId(),
                venue.getId(),
                category.getId(),
                "River Rafting",
                "Exciting river rafting experience",
                null,
                ProductStatus.ACTIVE));
    ProductVariant variant =
        productVariantRepository.save(
            new ProductVariant(product, "Adult", new BigDecimal("5000"), "JPY", 120, 10));
    variantId = variant.getId().toString();

    // Seed Spanner inventory slot
    slotId = UUID.randomUUID().toString();
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("inventory_slots")
                .set("slot_id")
                .to(slotId)
                .set("product_variant_id")
                .to(variantId)
                .set("slot_date")
                .to("2026-06-01")
                .set("start_time")
                .to("10:00")
                .set("end_time")
                .to("12:00")
                .set("total_capacity")
                .to(10)
                .set("reserved_count")
                .to(0)
                .set("created_at")
                .to(Timestamp.now())
                .build()));
  }

  @Test
  void browseProductsCreateOrderPayAndVerifyEntitlements() {
    // 1. Browse categories
    List<com.asoviewclone.commercecore.catalog.model.Category> categories =
        catalogService.listCategories();
    assertThat(categories).isNotEmpty();

    // 2. Browse products
    var products =
        catalogService.listProducts(
            null, ProductStatus.ACTIVE, org.springframework.data.domain.PageRequest.of(0, 10));
    assertThat(products.getContent()).isNotEmpty();
    assertThat(products.getContent().get(0).getTitle()).isEqualTo("River Rafting");

    // 3. Create order
    String idempotencyKey = UUID.randomUUID().toString();
    Order order =
        orderService.createOrder(
            userId,
            idempotencyKey,
            List.of(new CreateOrderItemRequest(variantId, slotId, 2, "5000")));
    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.totalAmount()).isEqualTo("10000");
    assertThat(order.items()).hasSize(1);

    // 4. Create payment
    Payment payment =
        paymentService.createPaymentIntent(order.orderId(), userId, UUID.randomUUID().toString());
    assertThat(payment.getStatus().name()).isEqualTo("PROCESSING");

    // 5. Confirm payment
    Payment confirmed = paymentService.confirmPayment(payment.getPaymentId().toString());
    assertThat(confirmed.getStatus().name()).isEqualTo("SUCCEEDED");

    // 6. Verify entitlements created
    List<Entitlement> entitlements = entitlementRepository.findByOrderId(order.orderId());
    assertThat(entitlements).hasSize(2); // quantity = 2

    // 7. Verify ticket passes created
    List<TicketPass> tickets = entitlementService.listUserTicketPasses(userId);
    assertThat(tickets).hasSize(2);
    assertThat(tickets.get(0).qrCodePayload()).startsWith("TKT-");

    // 8. Idempotency: same order returns existing
    Order duplicate =
        orderService.createOrder(
            userId,
            idempotencyKey,
            List.of(new CreateOrderItemRequest(variantId, slotId, 2, "5000")));
    assertThat(duplicate.orderId()).isEqualTo(order.orderId());
  }
}
