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
    List<PaymentConfirmationStep> steps = new ArrayList<>();
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
    } catch (Exception e) {
      throw new ConflictException(
          "Failed to persist saga steps for payment "
              + payment.getPaymentId()
              + ": "
              + e.getMessage());
    }

    List<PaymentConfirmationStep> confirmed = new ArrayList<>();
    for (PaymentConfirmationStep step : steps) {
      try {
        inventoryService.confirmHold(step.holdId());
        stepRepository.updateStatus(step.stepId(), PaymentConfirmationStepStatus.CONFIRMED);
        confirmed.add(step);
      } catch (Exception e) {
        log.warn(
            "Saga step failed for payment {} hold {}; compensating {} confirmed step(s)",
            payment.getPaymentId(),
            step.holdId(),
            confirmed.size(),
            e);
        for (PaymentConfirmationStep done : confirmed) {
          try {
            inventorySlotRepository.releaseConfirmedHold(done.slotId(), done.quantity());
            stepRepository.updateStatus(done.stepId(), PaymentConfirmationStepStatus.COMPENSATED);
          } catch (Exception compEx) {
            log.error(
                "Compensation failed for step {} slot {}", done.stepId(), done.slotId(), compEx);
          }
        }
        stepRepository.updateStatus(step.stepId(), PaymentConfirmationStepStatus.COMPENSATED);
        throw new ConflictException(
            "Saga compensation completed for payment "
                + payment.getPaymentId()
                + ": "
                + e.getMessage());
      }
    }
  }
}
