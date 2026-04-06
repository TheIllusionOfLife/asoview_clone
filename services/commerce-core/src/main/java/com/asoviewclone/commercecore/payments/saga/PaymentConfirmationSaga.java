package com.asoviewclone.commercecore.payments.saga;

import com.asoviewclone.commercecore.inventory.repository.InventorySlotRepository;
import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.commercecore.orders.model.Order;
import com.asoviewclone.commercecore.orders.model.OrderItem;
import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.common.error.ConflictException;
import com.asoviewclone.common.time.ClockProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Coordinates confirmation of multiple inventory holds for a single payment. Creates a durable
 * PENDING step row for each hold before attempting confirmation. On any failure, compensates all
 * already-confirmed steps by releasing reserved capacity and marks steps as COMPENSATED.
 */
@Component
public class PaymentConfirmationSaga {

  private static final Logger log = LoggerFactory.getLogger(PaymentConfirmationSaga.class);

  private final PaymentConfirmationStepRepository stepRepository;
  private final InventoryService inventoryService;
  private final InventorySlotRepository inventorySlotRepository;
  private final ClockProvider clockProvider;

  public PaymentConfirmationSaga(
      PaymentConfirmationStepRepository stepRepository,
      InventoryService inventoryService,
      InventorySlotRepository inventorySlotRepository,
      ClockProvider clockProvider) {
    this.stepRepository = stepRepository;
    this.inventoryService = inventoryService;
    this.inventorySlotRepository = inventorySlotRepository;
    this.clockProvider = clockProvider;
  }

  public void confirm(Payment payment, Order order) {
    Instant now = clockProvider.now();

    // Idempotent retry support: if a previous attempt for this payment inserted
    // step rows, reuse them. The unique index (payment_id, order_item_id) means
    // we cannot insert duplicates. Skip already-CONFIRMED/COMPENSATED steps and
    // re-attempt PENDING steps.
    List<PaymentConfirmationStep> existing =
        stepRepository.findByPaymentId(payment.getPaymentId().toString());
    List<PaymentConfirmationStep> steps;
    if (!existing.isEmpty()) {
      steps = new ArrayList<>(existing);
    } else {
      steps = new ArrayList<>();
      for (OrderItem item : order.items()) {
        if (item.holdId() == null) {
          continue;
        }
        steps.add(
            new PaymentConfirmationStep(
                UUID.randomUUID().toString(),
                payment.getPaymentId().toString(),
                item.orderItemId(),
                item.holdId(),
                item.slotId(),
                item.quantity(),
                PaymentConfirmationStepStatus.PENDING,
                now,
                now));
      }
      if (steps.isEmpty()) {
        return;
      }
      try {
        stepRepository.insertAll(steps);
      } catch (com.google.cloud.spanner.SpannerException se) {
        if (se.getErrorCode() == com.google.cloud.spanner.ErrorCode.ALREADY_EXISTS) {
          // A concurrent attempt inserted steps first. Re-read and resume.
          steps =
              new ArrayList<>(
                  stepRepository.findByPaymentId(payment.getPaymentId().toString()));
        } else {
          throw new ConflictException(
              "Failed to persist saga steps for payment "
                  + payment.getPaymentId()
                  + ": "
                  + se.getMessage());
        }
      } catch (Exception e) {
        throw new ConflictException(
            "Failed to persist saga steps for payment "
                + payment.getPaymentId()
                + ": "
                + e.getMessage());
      }
    }

    List<PaymentConfirmationStep> confirmed = new ArrayList<>();
    for (PaymentConfirmationStep step : steps) {
      // Skip steps that have already been processed by a prior attempt.
      if (step.status() == PaymentConfirmationStepStatus.CONFIRMED) {
        confirmed.add(step);
        continue;
      }
      if (step.status() == PaymentConfirmationStepStatus.COMPENSATED) {
        continue;
      }
      boolean holdConfirmed = false;
      try {
        inventoryService.confirmHold(step.holdId());
        // Hold is now confirmed in Spanner. Track this BEFORE attempting the step
        // status update so a failure of updateStatus does not leak the reservation.
        holdConfirmed = true;
        stepRepository.updateStatus(step.stepId(), PaymentConfirmationStepStatus.CONFIRMED);
        confirmed.add(step);
      } catch (Exception e) {
        log.warn(
            "Saga step failed for payment {} hold {}; compensating {} confirmed step(s)",
            payment.getPaymentId(),
            step.holdId(),
            confirmed.size(),
            e);
        // If we already confirmed the hold for the current step but the status
        // update threw, the reservation was applied. Compensate it before walking
        // the previously-confirmed list so capacity is fully released.
        if (holdConfirmed) {
          try {
            inventorySlotRepository.releaseConfirmedHold(step.slotId(), step.quantity());
          } catch (Exception compEx) {
            log.error(
                "Compensation failed for current step {} slot {}",
                step.stepId(),
                step.slotId(),
                compEx);
          }
        }
        for (PaymentConfirmationStep done : confirmed) {
          try {
            inventorySlotRepository.releaseConfirmedHold(done.slotId(), done.quantity());
            stepRepository.updateStatus(done.stepId(), PaymentConfirmationStepStatus.COMPENSATED);
          } catch (Exception compEx) {
            log.error(
                "Compensation failed for step {} slot {}", done.stepId(), done.slotId(), compEx);
          }
        }
        // Mark the failing step as FAILED (not COMPENSATED) so the recovery job
        // can retry it. COMPENSATED is reserved for steps that successfully
        // confirmed and were rolled back.
        stepRepository.updateStatus(step.stepId(), PaymentConfirmationStepStatus.FAILED);
        throw new ConflictException(
            "Saga compensation completed for payment "
                + payment.getPaymentId()
                + ": "
                + e.getMessage());
      }
    }
  }
}
