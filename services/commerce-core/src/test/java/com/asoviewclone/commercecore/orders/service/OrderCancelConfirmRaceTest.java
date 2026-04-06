package com.asoviewclone.commercecore.orders.service;

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
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Concurrency test for cancel/confirm CAS on orders. Verifies that when cancel and confirm race on
 * the same order, exactly one succeeds and the other throws ConflictException.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class OrderCancelConfirmRaceTest {

  @Autowired private OrderService orderService;
  @Autowired private PaymentService paymentService;
  @Autowired private OrderRepository orderRepository;
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
        tenantRepository.save(new Tenant("Race Operator", "race-op-" + UUID.randomUUID()));
    Venue venue =
        venueRepository.save(new Venue(tenant.getId(), "Race Venue", "Tokyo", 35.6, 139.7));
    Category category =
        categoryRepository.save(
            new Category(
                "RaceCat-" + UUID.randomUUID(), "race-" + UUID.randomUUID(), null, 1, null));
    Product product =
        productRepository.save(
            new Product(
                tenant.getId(),
                venue.getId(),
                category.getId(),
                "Race Product",
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
                .to("2026-07-01")
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
  void concurrentCancelAndConfirmExactlyOneWins() throws Exception {
    // Create an order and move it to PAYMENT_PENDING by creating a payment intent.
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(new OrderService.CreateOrderItemRequest(variantId, slotId, 1)));

    Payment payment =
        paymentService.createPaymentIntent(order.orderId(), userId, UUID.randomUUID().toString());

    // Wait (briefly) for AFTER_COMMIT listener to move order to PAYMENT_PENDING.
    waitForStatus(order.orderId(), OrderStatus.PAYMENT_PENDING);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    Callable<Void> cancel =
        () -> {
          start.await();
          try {
            orderService.cancelOrder(order.orderId());
            successes.incrementAndGet();
          } catch (Exception e) {
            // Either ConflictException (CAS lost) or ValidationException
            // (order already transitioned to a non-cancellable state like PAID).
            // Either way the loser is counted as a conflict.
            conflicts.incrementAndGet();
          }
          return null;
        };

    Callable<Void> confirm =
        () -> {
          start.await();
          try {
            paymentService.confirmPayment(payment.getPaymentId().toString());
            successes.incrementAndGet();
          } catch (Exception e) {
            conflicts.incrementAndGet();
          }
          return null;
        };

    Future<Void> f1 = executor.submit(cancel);
    Future<Void> f2 = executor.submit(confirm);
    start.countDown();
    try {
      f1.get();
      f2.get();
    } catch (ExecutionException e) {
      // ignore
    }
    executor.shutdown();

    // Exactly one of (cancel, confirm) should win; the other should see ConflictException.
    assertThat(successes.get() + conflicts.get()).isEqualTo(2);
    assertThat(successes.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(1);

    Order finalOrder = orderRepository.findById(order.orderId());
    assertThat(finalOrder.status()).isIn(OrderStatus.CANCELLED, OrderStatus.PAID);
  }

  private void waitForStatus(String orderId, OrderStatus expected) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 5_000;
    while (System.currentTimeMillis() < deadline) {
      if (orderRepository.findById(orderId).status() == expected) {
        return;
      }
      Thread.sleep(50);
    }
  }
}
