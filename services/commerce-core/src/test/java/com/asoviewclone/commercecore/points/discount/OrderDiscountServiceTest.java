package com.asoviewclone.commercecore.points.discount;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.points.service.PointService;
import com.asoviewclone.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderDiscountServiceTest {

  private OrderDiscountRepository repo;
  private PointService pointService;
  private OrderDiscountService service;

  private final String orderId = "order-1";
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    repo = mock(OrderDiscountRepository.class);
    pointService = mock(PointService.class);
    service = new OrderDiscountService(repo, pointService);
  }

  @Test
  void applyPointsBurnDiscount_happy() {
    when(pointService.getBalance(userId)).thenReturn(500L);
    when(repo.findByOrderId(orderId)).thenReturn(Optional.empty());

    service.applyPointsBurnDiscount(orderId, userId, 100L, new BigDecimal("1000"));

    verify(pointService).burn(userId, 100L, orderId);
    verify(repo).save(any(OrderDiscount.class));
  }

  @Test
  void applyPointsBurnDiscount_zero_noops() {
    service.applyPointsBurnDiscount(orderId, userId, 0L, new BigDecimal("1000"));
    verify(pointService, never()).burn(any(), anyLong(), any());
  }

  @Test
  void applyPointsBurnDiscount_exceedsBalance_throws() {
    when(pointService.getBalance(userId)).thenReturn(50L);
    assertThatThrownBy(
            () -> service.applyPointsBurnDiscount(orderId, userId, 100L, new BigDecimal("1000")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void applyPointsBurnDiscount_exceedsSubtotal_throws() {
    when(pointService.getBalance(userId)).thenReturn(10000L);
    assertThatThrownBy(
            () -> service.applyPointsBurnDiscount(orderId, userId, 500L, new BigDecimal("200")))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void refundForCancelledOrder_refundsAndDeletes() {
    OrderDiscount d = new OrderDiscount(orderId, OrderDiscountService.DISCOUNT_TYPE_POINTS, 100);
    when(repo.findByOrderId(orderId)).thenReturn(Optional.of(d));

    service.refundForCancelledOrder(orderId, userId);

    verify(pointService).refund(userId, 100L, orderId);
    verify(repo).deleteByOrderId(orderId);
  }

  @Test
  void refundForCancelledOrder_noDiscount_noops() {
    when(repo.findByOrderId(orderId)).thenReturn(Optional.empty());
    service.refundForCancelledOrder(orderId, userId);
    verify(pointService, never()).refund(any(), anyLong(), any());
  }
}
