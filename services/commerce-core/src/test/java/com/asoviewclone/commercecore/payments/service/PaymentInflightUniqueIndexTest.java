package com.asoviewclone.commercecore.payments.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.asoviewclone.common.error.ConflictException;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Direct test for the partial unique index on {@code payments(order_id) WHERE status IN
 * ('CREATED','PROCESSING')} that enforces a single in-flight payment per order. Two threads race to
 * call {@code createPaymentIntent} for the same order with different idempotency keys; exactly one
 * must succeed and the other must throw {@link ConflictException}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class PaymentInflightUniqueIndexTest {

  @Autowired private OrderService orderService;
  @Autowired private PaymentService paymentService;
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
    Tenant tenant =
        tenantRepository.save(new Tenant("Inflight Op", "inflight-op-" + UUID.randomUUID()));
    Venue venue =
        venueRepository.save(new Venue(tenant.getId(), "Inflight Venue", "Tokyo", 35.6, 139.7));
    Category category =
        categoryRepository.save(
            new Category(
                "InflightCat-" + UUID.randomUUID(),
                "inflight-" + UUID.randomUUID(),
                null,
                1,
                null));
    Product product =
        productRepository.save(
            new Product(
                tenant.getId(),
                venue.getId(),
                category.getId(),
                "Inflight Product",
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
                .to("2026-09-01")
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
  void concurrentCreatePaymentIntentForSameOrderExactlyOneSucceeds() throws Exception {
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(new OrderService.CreateOrderItemRequest(variantId, slotId, 1)));

    String idem1 = UUID.randomUUID().toString();
    String idem2 = UUID.randomUUID().toString();

    CountDownLatch startLatch = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    AtomicReference<Throwable> unexpected = new AtomicReference<>();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> f1 =
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  Payment p = paymentService.createPaymentIntent(order.orderId(), userId, idem1);
                  if (p != null) {
                    successes.incrementAndGet();
                  }
                } catch (ConflictException ce) {
                  if (ce.getMessage() != null
                      && ce.getMessage().contains("already has a payment in flight")) {
                    conflicts.incrementAndGet();
                  } else {
                    unexpected.set(ce);
                  }
                } catch (Throwable t) {
                  unexpected.set(t);
                }
              });
      Future<?> f2 =
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  Payment p = paymentService.createPaymentIntent(order.orderId(), userId, idem2);
                  if (p != null) {
                    successes.incrementAndGet();
                  }
                } catch (ConflictException ce) {
                  if (ce.getMessage() != null
                      && ce.getMessage().contains("already has a payment in flight")) {
                    conflicts.incrementAndGet();
                  } else {
                    unexpected.set(ce);
                  }
                } catch (Throwable t) {
                  unexpected.set(t);
                }
              });

      startLatch.countDown();
      f1.get(30, TimeUnit.SECONDS);
      f2.get(30, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    assertThat(unexpected.get()).as("unexpected exception").isNull();
    assertThat(successes.get()).as("exactly one success").isEqualTo(1);
    assertThat(conflicts.get()).as("exactly one in-flight conflict").isEqualTo(1);
  }
}
