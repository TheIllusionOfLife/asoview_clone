package com.asoviewclone.commercecore.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

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
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Verifies the AFTER_COMMIT event listener path for advancing order status to PAYMENT_PENDING
 * after the JPA payment commit. Uses a spied OrderRepository to inject transient failures and
 * verify the @Retryable backoff succeeds, and that a permanent failure leaves the payment row but
 * the order in PENDING without bubbling exceptions to the caller.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class PaymentCrossStoreConsistencyTest {

  @Autowired private OrderService orderService;
  @Autowired private PaymentService paymentService;
  @MockitoSpyBean private OrderRepository orderRepository;
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
    reset(orderRepository);
    Tenant tenant =
        tenantRepository.save(new Tenant("Xstore Operator", "xstore-op-" + UUID.randomUUID()));
    Venue venue =
        venueRepository.save(new Venue(tenant.getId(), "Xstore Venue", "Tokyo", 35.6, 139.7));
    Category category =
        categoryRepository.save(
            new Category(
                "XstoreCat-" + UUID.randomUUID(),
                "xstore-" + UUID.randomUUID(),
                null,
                1,
                null));
    Product product =
        productRepository.save(
            new Product(
                tenant.getId(),
                venue.getId(),
                category.getId(),
                "Xstore Product",
                "desc",
                null,
                ProductStatus.ACTIVE));
    ProductVariant variant =
        productVariantRepository.save(
            new ProductVariant(product, "Adult", new BigDecimal("5000"), "JPY", 60, 10));
    variantId = variant.getId().toString();

    slotId = UUID.randomUUID().toString();
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("inventory_slots")
                .set("slot_id")
                .to(slotId)
                .set("product_variant_id")
                .to(variantId)
                .set("slot_date")
                .to("2026-08-01")
                .set("start_time")
                .to("09:00")
                .set("end_time")
                .to("10:00")
                .set("total_capacity")
                .to(5)
                .set("reserved_count")
                .to(0)
                .set("created_at")
                .to(Timestamp.now())
                .build()));
  }

  @Test
  void afterCommitListenerRetriesAndEventuallyAdvancesOrder() throws Exception {
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(new OrderService.CreateOrderItemRequest(variantId, slotId, 1)));

    // Fail the first 2 calls to updateStatusIf for the PENDING->PAYMENT_PENDING transition,
    // succeed on the 3rd.
    AtomicInteger calls = new AtomicInteger();
    doAnswer(
            inv -> {
              OrderStatus expected = inv.getArgument(1);
              OrderStatus target = inv.getArgument(2);
              if (expected == OrderStatus.PENDING && target == OrderStatus.PAYMENT_PENDING) {
                if (calls.incrementAndGet() <= 2) {
                  throw new RuntimeException("transient spanner failure");
                }
              }
              return inv.callRealMethod();
            })
        .when(orderRepository)
        .updateStatusIf(anyString(), any(OrderStatus.class), any(OrderStatus.class));

    Payment payment =
        paymentService.createPaymentIntent(
            order.orderId(), userId, UUID.randomUUID().toString());
    assertThat(payment).isNotNull();

    // Wait for retry to eventually succeed.
    long deadline = System.currentTimeMillis() + 10_000;
    OrderStatus finalStatus = null;
    while (System.currentTimeMillis() < deadline) {
      finalStatus = orderRepository.findById(order.orderId()).status();
      if (finalStatus == OrderStatus.PAYMENT_PENDING) {
        break;
      }
      Thread.sleep(100);
    }
    assertThat(finalStatus).isEqualTo(OrderStatus.PAYMENT_PENDING);
    verify(orderRepository, atLeast(3))
        .updateStatusIf(
            eq(order.orderId()),
            eq(OrderStatus.PENDING),
            eq(OrderStatus.PAYMENT_PENDING));
  }

  @Test
  void permanentFailureLeavesPaymentRowAndOrderInPending() {
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(new OrderService.CreateOrderItemRequest(variantId, slotId, 1)));

    doThrow(new RuntimeException("permanent spanner failure"))
        .when(orderRepository)
        .updateStatusIf(
            eq(order.orderId()),
            eq(OrderStatus.PENDING),
            eq(OrderStatus.PAYMENT_PENDING));

    // The listener swallows the final failure (logged only); the caller must not see the
    // exception.
    Payment payment =
        paymentService.createPaymentIntent(
            order.orderId(), userId, UUID.randomUUID().toString());
    assertThat(payment).isNotNull();

    // Order stays in PENDING; payment row exists.
    Order reloaded = orderRepository.findById(order.orderId());
    assertThat(reloaded.status()).isEqualTo(OrderStatus.PENDING);
  }
}
