package com.asoviewclone.commercecore.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.catalog.model.Product;
import com.asoviewclone.commercecore.catalog.model.ProductVariant;
import com.asoviewclone.commercecore.catalog.repository.ProductVariantRepository;
import com.asoviewclone.commercecore.inventory.model.InventoryHold;
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.points.discount.OrderDiscountService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Regression tests for the silent overcharge bug caught by Codex on PR #21 review round 3.
 *
 * <p>Background: {@code OrderServiceImpl.createOrder} burns points and writes the discount row, but
 * the original implementation forgot to subtract the burned points from the order's {@code
 * totalAmount}. The Spanner row was saved with the gross amount and {@code
 * PaymentServiceImpl.createPaymentIntent} subsequently created a Stripe intent for the gross amount
 * — the user lost their points AND was charged full price.
 *
 * <p>The test that would have caught this on the very first run is one assertion: "create order
 * with {@code pointsToUse=N}, assert {@code saved.totalAmount() == gross - N}." This file owns that
 * assertion and a sibling for the no-points happy path. CLAUDE.md "Process Lessons (PR #21)"
 * codifies the underlying rule.
 */
class OrderServiceImplPointsBurnTest {

  private OrderRepository orderRepository;
  private InventoryService inventoryService;
  private ProductVariantRepository productVariantRepository;
  private OrderDiscountService orderDiscountService;
  private ApplicationEventPublisher eventPublisher;
  private OrderServiceImpl service;

  private final String userId = UUID.randomUUID().toString();
  private final UUID variantId = UUID.randomUUID();
  private final String slotId = "slot-1";
  private final String idempotencyKey = "idem-1";

  @BeforeEach
  void setUp() {
    orderRepository = mock(OrderRepository.class);
    inventoryService = mock(InventoryService.class);
    productVariantRepository = mock(ProductVariantRepository.class);
    orderDiscountService = mock(OrderDiscountService.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    service =
        new OrderServiceImpl(
            orderRepository,
            inventoryService,
            productVariantRepository,
            orderDiscountService,
            eventPublisher);

    when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

    InventoryHold hold =
        new InventoryHold(
            "hold-1",
            slotId,
            variantId.toString(),
            userId,
            1L,
            java.time.Instant.now().plusSeconds(300),
            java.time.Instant.now());
    when(inventoryService.holdInventory(eq(slotId), eq(userId), anyInt())).thenReturn(hold);

    Product product = newInstance(Product.class);
    ProductVariant variant = newInstance(ProductVariant.class);
    setField(variant, "id", variantId);
    setField(variant, "product", product);
    setField(variant, "priceAmount", new BigDecimal("1500.00"));
    setField(variant, "priceCurrency", "JPY");
    when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

    // Stub saveWithId to echo back its 3rd arg as totalAmount so the test can assert it.
    when(orderRepository.saveWithId(
            anyString(), anyString(), anyString(), anyString(), anyString(), any()))
        .thenAnswer(
            inv -> {
              String orderId = inv.getArgument(0);
              String uid = inv.getArgument(1);
              String total = inv.getArgument(2);
              String currency = inv.getArgument(3);
              String idem = inv.getArgument(4);
              List<OrderItem> items = inv.getArgument(5);
              return new Order(
                  orderId,
                  uid,
                  com.asoviewclone.commercecore.orders.model.OrderStatus.PENDING,
                  total,
                  currency,
                  idem,
                  items,
                  java.time.Instant.now(),
                  java.time.Instant.now());
            });
  }

  @Test
  void createOrder_withPointsBurn_subtractsFromTotalAmount() {
    // Gross = 1500 (1 unit x 1500 JPY). pointsToUse = 100. Expected payable total = 1400.
    Order saved =
        service.createOrder(
            userId,
            idempotencyKey,
            List.of(new OrderService.CreateOrderItemRequest(variantId.toString(), slotId, 1)),
            100);

    assertThat(new BigDecimal(saved.totalAmount()))
        .as("payable total must equal gross - pointsToUse")
        .isEqualByComparingTo(new BigDecimal("1400"));

    // The discount service must have been called with the gross subtotal (it computes
    // its own validation against subtotal); we just verify it was invoked at all.
    verify(orderDiscountService)
        .applyPointsBurnDiscount(
            anyString(), eq(UUID.fromString(userId)), eq(100L), any(BigDecimal.class));

    // The Spanner save must have been called with the DISCOUNTED total, not the gross.
    ArgumentCaptor<String> totalCaptor = ArgumentCaptor.forClass(String.class);
    verify(orderRepository)
        .saveWithId(
            anyString(), anyString(), totalCaptor.capture(), anyString(), anyString(), any());
    assertThat(new BigDecimal(totalCaptor.getValue())).isEqualByComparingTo(new BigDecimal("1400"));
  }

  @Test
  void createOrder_withoutPointsBurn_passesGrossTotal() {
    Order saved =
        service.createOrder(
            userId,
            idempotencyKey,
            List.of(new OrderService.CreateOrderItemRequest(variantId.toString(), slotId, 2)),
            0);

    // 2 units x 1500 = 3000 JPY, no points burned, no discount.
    assertThat(new BigDecimal(saved.totalAmount())).isEqualByComparingTo(new BigDecimal("3000"));
    verify(orderDiscountService, never())
        .applyPointsBurnDiscount(anyString(), any(), anyInt(), any());
  }

  @Test
  void createOrder_pointsLargerThanTotal_clampsAtZero() {
    // Defensive: even if someone somehow bypasses OrderDiscountService validation
    // and burns 5000 points on a 1500 yen order, the payable must clamp at 0,
    // never go negative (would crash Stripe with "negative amount").
    Order saved =
        service.createOrder(
            userId,
            idempotencyKey,
            List.of(new OrderService.CreateOrderItemRequest(variantId.toString(), slotId, 1)),
            5000);

    assertThat(new BigDecimal(saved.totalAmount())).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // Reflection helpers because Product/ProductVariant use field-only JPA mapping
  // with protected no-arg constructors and no setters.
  private static <T> T newInstance(Class<T> cls) {
    try {
      java.lang.reflect.Constructor<T> c = cls.getDeclaredConstructor();
      c.setAccessible(true);
      return c.newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setField(Object target, String name, Object value) {
    try {
      java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }
}
