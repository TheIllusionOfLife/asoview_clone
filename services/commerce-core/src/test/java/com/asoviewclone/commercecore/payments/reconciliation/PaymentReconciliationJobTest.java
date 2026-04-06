package com.asoviewclone.commercecore.payments.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.orders.model.OrderStatus;
import com.asoviewclone.commercecore.orders.repository.OrderRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies the cross-store reconciliation paths added by the follow-up review:
 *
 * <ul>
 *   <li>PROCESSING payment + PAID order is promoted to SUCCEEDED.
 *   <li>PROCESSING payment + CANCELLED order is marked FAILED.
 *   <li>PROCESSING payment + PENDING order has the order advanced to PAYMENT_PENDING (the
 *       AFTER_COMMIT listener exhausted its retries).
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class PaymentReconciliationJobTest {

  @Autowired private PaymentReconciliationJob job;
  @Autowired private PaymentRepository paymentRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private DatabaseClient spannerClient;

  private String seedOrder(OrderStatus status) {
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
                .to(status.name())
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
    return orderId;
  }

  private Payment seedProcessingPayment(String orderId) {
    Payment p =
        new Payment(
            orderId,
            UUID.randomUUID().toString(),
            new BigDecimal("1000"),
            "JPY",
            "idem-" + UUID.randomUUID());
    p.setStatus(PaymentStatus.PROCESSING);
    return paymentRepository.saveAndFlush(p);
  }

  @Test
  void promotesProcessingPaymentWhenOrderIsPaid() {
    String orderId = seedOrder(OrderStatus.PAID);
    Payment payment = seedProcessingPayment(orderId);

    job.reconcileProcessingPayments();

    Payment reloaded = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
  }

  @Test
  void marksProcessingPaymentFailedWhenOrderIsCancelled() {
    String orderId = seedOrder(OrderStatus.CANCELLED);
    Payment payment = seedProcessingPayment(orderId);

    job.reconcileProcessingPayments();

    Payment reloaded = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void advancesStuckPendingOrderToPaymentPending() {
    String orderId = seedOrder(OrderStatus.PENDING);
    Payment payment = seedProcessingPayment(orderId);

    job.reconcileProcessingPayments();

    // Payment row stays PROCESSING because the order is now PAYMENT_PENDING,
    // which is the harmless "in-flight" state — only the order should advance.
    assertThat(orderRepository.findById(orderId).status())
        .isEqualTo(OrderStatus.PAYMENT_PENDING);
    Payment reloaded = paymentRepository.findById(payment.getPaymentId()).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
  }
}
