package com.asoviewclone.commercecore.payments.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.orders.event.OrderPaidEvent;
import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentStatus;
import com.asoviewclone.commercecore.payments.repository.PaymentRepository;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pitfall 13 (PR #21): a reconciliation job that repairs cross-store divergence (Spanner order
 * {@code PAID} ↔ Postgres payment {@code PROCESSING}) MUST re-publish the same domain events the
 * happy path emits. Otherwise recovered orders are functionally second-class — no points earned, no
 * email sent, no analytics fired.
 *
 * <p>This test seeds the divergent state, runs the reconciliation, and asserts that an {@link
 * OrderPaidEvent} was delivered to an {@code @TransactionalEventListener(AFTER_COMMIT)} recorder.
 * If anyone reverts {@code PaymentReconciliationJob.reconcileBatch} to skip the {@code
 * eventPublisher.publishEvent(...)} call, this test fails.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({
  PostgresContainerConfig.class,
  RedisContainerConfig.class,
  SpannerEmulatorConfig.class,
  OrderPaidEventReconciliationTest.RecorderConfig.class
})
class OrderPaidEventReconciliationTest {

  @Autowired private PaymentReconciliationJob job;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private DatabaseClient spannerClient;
  @Autowired private OrderPaidEventRecorder recorder;

  @Test
  void reconcilingPaidOrderRepublishesOrderPaidEvent() {
    String orderId = UUID.randomUUID().toString();
    String userId = UUID.randomUUID().toString();
    Timestamp now = Timestamp.now();
    spannerClient.write(
        List.of(
            Mutation.newInsertBuilder("orders")
                .set("order_id")
                .to(orderId)
                .set("user_id")
                .to(userId)
                .set("status")
                .to(OrderStatus.PAID.name())
                .set("total_amount")
                .to("1000")
                .set("currency")
                .to("JPY")
                .set("idempotency_key")
                .to("idem-" + UUID.randomUUID())
                .set("created_at")
                .to(now)
                .set("updated_at")
                .to(now)
                .build()));

    Payment payment =
        new Payment(orderId, userId, new BigDecimal("1000"), "JPY", "idem-" + UUID.randomUUID());
    payment.setStatus(PaymentStatus.PROCESSING);
    paymentRepository.saveAndFlush(payment);

    recorder.events.clear();

    job.reconcileProcessingPayments();

    Payment reloaded = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    assertThat(recorder.events)
        .as("OrderPaidEvent must be re-published by the reconciliation job")
        .anySatisfy(e -> assertThat(e.orderId()).isEqualTo(orderId));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RecorderConfig {
    @Bean
    OrderPaidEventRecorder orderPaidEventRecorder() {
      return new OrderPaidEventRecorder();
    }
  }

  @Component
  static class OrderPaidEventRecorder {
    final List<OrderPaidEvent> events = new CopyOnWriteArrayList<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPaidEvent e) {
      events.add(e);
    }
  }
}
