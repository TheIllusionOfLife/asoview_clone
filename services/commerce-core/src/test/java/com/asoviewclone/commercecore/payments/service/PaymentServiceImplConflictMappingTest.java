package com.asoviewclone.commercecore.payments.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.repository.PaymentRepository;
import com.asoviewclone.commercecore.payments.saga.PaymentConfirmationSaga;
import com.asoviewclone.common.error.ConflictException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Guards the catch block in {@link PaymentServiceImpl#createPaymentIntent} so that only the {@code
 * uniq_payments_inflight_order} partial unique index surfaces as a {@link ConflictException}
 * ("already has a payment in flight"). Every other integrity violation must propagate with its real
 * cause, so the next VARCHAR-overflow-class bug gets diagnosed instead of misdiagnosed as a
 * uniqueness conflict the way the PR #103 incident was for months.
 */
class PaymentServiceImplConflictMappingTest {

  private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
  private final OrderRepository orderRepository = mock(OrderRepository.class);
  private final InventoryService inventoryService = mock(InventoryService.class);
  private final PaymentGateway paymentGateway = mock(PaymentGateway.class);
  private final EntitlementCreator entitlementCreator = mock(EntitlementCreator.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final PaymentConfirmationSaga paymentConfirmationSaga =
      mock(PaymentConfirmationSaga.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);

  private final PaymentServiceImpl service =
      new PaymentServiceImpl(
          paymentRepository,
          orderRepository,
          inventoryService,
          paymentGateway,
          entitlementCreator,
          eventPublisher,
          paymentConfirmationSaga,
          transactionManager);

  private final String orderId = UUID.randomUUID().toString();
  private final String userId = UUID.randomUUID().toString();
  private final String idempotencyKey = UUID.randomUUID().toString();

  private void stubHappyPathUntilFlush() {
    when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
    Order order =
        new Order(
            orderId,
            userId,
            OrderStatus.PENDING,
            "5000",
            "JPY",
            idempotencyKey,
            List.of(),
            null,
            null);
    when(orderRepository.findById(orderId)).thenReturn(order);
    when(paymentGateway.createIntent(anyString(), any(BigDecimal.class), anyString(), anyString()))
        .thenReturn(new PaymentGateway.PaymentResult("pi_test", "pi_test_secret", true));
    when(paymentGateway.providerName()).thenReturn("test-gateway");
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void mapsInflightConstraintViolationToConflict() {
    stubHappyPathUntilFlush();
    ConstraintViolationException cve =
        new ConstraintViolationException(
            "duplicate key", new SQLException("unique_violation"), "uniq_payments_inflight_order");
    doThrow(new DataIntegrityViolationException("dup", cve)).when(paymentRepository).flush();

    assertThatThrownBy(() -> service.createPaymentIntent(orderId, userId, idempotencyKey))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already has a payment in flight");
  }

  @Test
  void rethrowsOtherConstraintViolations() {
    stubHappyPathUntilFlush();
    ConstraintViolationException cve =
        new ConstraintViolationException(
            "value too long for type character varying(128)",
            new SQLException("string_data_right_truncation", "22001"),
            "some_other_constraint");
    doThrow(new DataIntegrityViolationException("truncation", cve)).when(paymentRepository).flush();

    assertThatThrownBy(() -> service.createPaymentIntent(orderId, userId, idempotencyKey))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("truncation");
  }

  @Test
  void rethrowsWhenConstraintNameIsNull() {
    stubHappyPathUntilFlush();
    ConstraintViolationException cve =
        new ConstraintViolationException(
            "integrity violation with no constraint name", new SQLException("integrity"), null);
    doThrow(new DataIntegrityViolationException("no name", cve)).when(paymentRepository).flush();

    assertThatThrownBy(() -> service.createPaymentIntent(orderId, userId, idempotencyKey))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("no name");
  }
}
