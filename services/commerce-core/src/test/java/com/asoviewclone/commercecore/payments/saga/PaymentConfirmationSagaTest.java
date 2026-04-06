package com.asoviewclone.commercecore.payments.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

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
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.orders.service.OrderService;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.time.ClockProvider;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class PaymentConfirmationSagaTest {

  @Autowired private PaymentConfirmationSaga saga;
  @Autowired private PaymentConfirmationStepRepository stepRepository;
  @Autowired private SagaRecoveryJob sagaRecoveryJob;
  @MockitoSpyBean private InventoryService inventoryService;
  @Autowired private OrderService orderService;
  @Autowired private OrderRepository orderRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private ProductVariantRepository productVariantRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private VenueRepository venueRepository;
  @Autowired private DatabaseClient spannerClient;
  @Autowired private ClockProvider clockProvider;

  private String variantId;
  private String slotIdA;
  private String slotIdB;
  private final String userId = UUID.randomUUID().toString();

  @BeforeEach
  void setUp() {
    reset(inventoryService);
    Tenant tenant = tenantRepository.save(new Tenant("Saga Op", "saga-op-" + UUID.randomUUID()));
    Venue venue =
        venueRepository.save(new Venue(tenant.getId(), "Saga Venue", "Tokyo", 35.6, 139.7));
    Category category =
        categoryRepository.save(
            new Category(
                "SagaCat-" + UUID.randomUUID(), "saga-" + UUID.randomUUID(), null, 1, null));
    Product product =
        productRepository.save(
            new Product(
                tenant.getId(),
                venue.getId(),
                category.getId(),
                "Saga Product",
                "desc",
                null,
                ProductStatus.ACTIVE));
    ProductVariant variant =
        productVariantRepository.save(
            new ProductVariant(product, "Adult", new BigDecimal("5000"), "JPY", 60, 10));
    variantId = variant.getId().toString();

    slotIdA = insertSlot("2026-10-01");
    slotIdB = insertSlot("2026-10-02");
  }

  private String insertSlot(String date) {
    String slotId = UUID.randomUUID().toString();
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("inventory_slots")
                .set("slot_id")
                .to(slotId)
                .set("product_variant_id")
                .to(variantId)
                .set("slot_date")
                .to(date)
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
    return slotId;
  }

  private long readReserved(String slotId) {
    Statement stmt =
        Statement.newBuilder("SELECT reserved_count FROM inventory_slots WHERE slot_id = @s")
            .bind("s")
            .to(slotId)
            .build();
    try (ResultSet rs = spannerClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getLong("reserved_count");
      }
    }
    return -1;
  }

  private Payment makeFakePayment() {
    Payment p =
        new Payment("order-x", userId, new BigDecimal("1000"), "JPY", "idem-" + UUID.randomUUID());
    try {
      Field f = Payment.class.getDeclaredField("paymentId");
      f.setAccessible(true);
      f.set(p, UUID.randomUUID());
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    return p;
  }

  @Test
  void confirmHappyPathMarksAllStepsConfirmedAndIncrementsReserved() {
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(
                new OrderService.CreateOrderItemRequest(variantId, slotIdA, 1),
                new OrderService.CreateOrderItemRequest(variantId, slotIdB, 2)));

    Payment payment = makeFakePayment();
    saga.confirm(payment, order);

    List<PaymentConfirmationStep> steps =
        stepRepository.findByPaymentId(payment.getPaymentId().toString());
    assertThat(steps).hasSize(2);
    assertThat(steps).allMatch(s -> s.status() == PaymentConfirmationStepStatus.CONFIRMED);
    assertThat(readReserved(slotIdA)).isEqualTo(1);
    assertThat(readReserved(slotIdB)).isEqualTo(2);
  }

  @Test
  void confirmCompensatesPriorConfirmedStepsOnFailure() {
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(
                new OrderService.CreateOrderItemRequest(variantId, slotIdA, 1),
                new OrderService.CreateOrderItemRequest(variantId, slotIdB, 2)));

    String secondHoldId = order.items().get(1).holdId();
    doCallRealMethod().when(inventoryService).confirmHold(eq(order.items().get(0).holdId()));
    doThrow(new RuntimeException("boom")).when(inventoryService).confirmHold(eq(secondHoldId));

    Payment payment = makeFakePayment();
    assertThatThrownBy(() -> saga.confirm(payment, order))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Saga compensation completed");

    // First slot's reserved_count was incremented then released back to 0.
    assertThat(readReserved(slotIdA)).isZero();
    // Second slot never got incremented.
    assertThat(readReserved(slotIdB)).isZero();

    List<PaymentConfirmationStep> steps =
        stepRepository.findByPaymentId(payment.getPaymentId().toString());
    assertThat(steps).hasSize(2);
    // The first step was confirmed then rolled back -> COMPENSATED.
    // The second (failing) step never confirmed -> FAILED so the recovery job retries it.
    assertThat(steps)
        .extracting(PaymentConfirmationStep::status)
        .containsExactlyInAnyOrder(
            PaymentConfirmationStepStatus.COMPENSATED, PaymentConfirmationStepStatus.FAILED);
  }

  @Test
  void recoveryJobConfirmsStalePendingStep() {
    // Create an order to get a real hold.
    Order order =
        orderService.createOrder(
            userId,
            UUID.randomUUID().toString(),
            List.of(new OrderService.CreateOrderItemRequest(variantId, slotIdA, 1)));
    OrderItem item = order.items().get(0);

    // Insert a stale PENDING step pointing at the real hold.
    String stepId = UUID.randomUUID().toString();
    Instant stale = clockProvider.now().minusSeconds(600);
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("payment_confirmation_steps")
                .set("step_id")
                .to(stepId)
                .set("payment_id")
                .to(UUID.randomUUID().toString())
                .set("order_item_id")
                .to(item.orderItemId())
                .set("hold_id")
                .to(item.holdId())
                .set("slot_id")
                .to(item.slotId())
                .set("quantity")
                .to(item.quantity())
                .set("status")
                .to("PENDING")
                .set("attempted_at")
                .to(Timestamp.ofTimeSecondsAndNanos(stale.getEpochSecond(), 0))
                .set("updated_at")
                .to(Timestamp.ofTimeSecondsAndNanos(stale.getEpochSecond(), 0))
                .build()));

    sagaRecoveryJob.recoverStalePending();

    // Step must now be CONFIRMED.
    Statement stmt =
        Statement.newBuilder("SELECT status FROM payment_confirmation_steps WHERE step_id = @id")
            .bind("id")
            .to(stepId)
            .build();
    String status = null;
    try (ResultSet rs = spannerClient.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        status = rs.getString("status");
      }
    }
    assertThat(status).isEqualTo("CONFIRMED");
    assertThat(readReserved(slotIdA)).isEqualTo(1);
  }
}
