package com.asoviewclone.commercecore.payments.saga;

import com.asoviewclone.common.time.ClockProvider;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentConfirmationStepRepository {

  private final DatabaseClient databaseClient;
  private final ClockProvider clockProvider;

  public PaymentConfirmationStepRepository(
      DatabaseClient databaseClient, ClockProvider clockProvider) {
    this.databaseClient = databaseClient;
    this.clockProvider = clockProvider;
  }

  public void insertAll(List<PaymentConfirmationStep> steps) {
    if (steps.isEmpty()) {
      return;
    }
    List<Mutation> mutations = new ArrayList<>(steps.size());
    for (PaymentConfirmationStep step : steps) {
      mutations.add(
          Mutation.newInsertBuilder("payment_confirmation_steps")
              .set("step_id")
              .to(step.stepId())
              .set("payment_id")
              .to(step.paymentId())
              .set("order_item_id")
              .to(step.orderItemId())
              .set("hold_id")
              .to(step.holdId())
              .set("slot_id")
              .to(step.slotId())
              .set("quantity")
              .to(step.quantity())
              .set("status")
              .to(step.status().name())
              .set("attempted_at")
              .to(Timestamp.ofTimeSecondsAndNanos(step.attemptedAt().getEpochSecond(), 0))
              .set("updated_at")
              .to(Timestamp.ofTimeSecondsAndNanos(step.updatedAt().getEpochSecond(), 0))
              .build());
    }
    databaseClient.write(mutations);
  }

  public List<PaymentConfirmationStep> findByPaymentId(String paymentId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT step_id, payment_id, order_item_id, hold_id, slot_id, quantity,"
                    + " status, attempted_at, updated_at"
                    + " FROM payment_confirmation_steps WHERE payment_id = @paymentId")
            .bind("paymentId")
            .to(paymentId)
            .build();
    List<PaymentConfirmationStep> out = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        out.add(map(rs));
      }
    }
    return out;
  }

  public void updateStatus(String stepId, PaymentConfirmationStepStatus newStatus) {
    Instant now = clockProvider.now();
    databaseClient.write(
        List.of(
            Mutation.newUpdateBuilder("payment_confirmation_steps")
                .set("step_id")
                .to(stepId)
                .set("status")
                .to(newStatus.name())
                .set("updated_at")
                .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
                .build()));
  }

  public List<PaymentConfirmationStep> findStalePending(Instant threshold) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT step_id, payment_id, order_item_id, hold_id, slot_id, quantity,"
                    + " status, attempted_at, updated_at"
                    + " FROM payment_confirmation_steps"
                    + " WHERE status IN ('PENDING', 'FAILED') AND attempted_at < @threshold")
            .bind("threshold")
            .to(Timestamp.ofTimeSecondsAndNanos(threshold.getEpochSecond(), 0))
            .build();
    List<PaymentConfirmationStep> out = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        out.add(map(rs));
      }
    }
    return out;
  }

  private PaymentConfirmationStep map(ResultSet rs) {
    return new PaymentConfirmationStep(
        rs.getString("step_id"),
        rs.getString("payment_id"),
        rs.getString("order_item_id"),
        rs.getString("hold_id"),
        rs.getString("slot_id"),
        rs.getLong("quantity"),
        PaymentConfirmationStepStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("attempted_at").toDate().toInstant(),
        rs.getTimestamp("updated_at").toDate().toInstant());
  }
}
