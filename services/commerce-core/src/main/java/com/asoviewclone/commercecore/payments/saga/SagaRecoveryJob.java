package com.asoviewclone.commercecore.payments.saga;

import com.asoviewclone.commercecore.inventory.service.InventoryService;
import com.asoviewclone.common.time.ClockProvider;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically retries saga steps stuck in PENDING beyond a stale threshold. Leaves failures for
 * the next run. Runs every 60 seconds.
 */
@Component
public class SagaRecoveryJob {

  private static final Logger log = LoggerFactory.getLogger(SagaRecoveryJob.class);
  private static final Duration STALE_AFTER = Duration.ofMinutes(5);

  private final PaymentConfirmationStepRepository stepRepository;
  private final InventoryService inventoryService;
  private final ClockProvider clockProvider;

  public SagaRecoveryJob(
      PaymentConfirmationStepRepository stepRepository,
      InventoryService inventoryService,
      ClockProvider clockProvider) {
    this.stepRepository = stepRepository;
    this.inventoryService = inventoryService;
    this.clockProvider = clockProvider;
  }

  @Scheduled(fixedDelay = 60_000)
  public void recoverStalePending() {
    Instant threshold = clockProvider.now().minus(STALE_AFTER);
    for (PaymentConfirmationStep step : stepRepository.findStalePending(threshold)) {
      try {
        // If any sibling step for the same payment is already COMPENSATED, the
        // saga's compensation has already run for the payment as a whole.
        // Re-confirming this orphan step would re-consume inventory the user
        // is no longer paying for. Mark it COMPENSATED (terminal) and skip.
        boolean hasCompensatedSibling =
            stepRepository.findByPaymentId(step.paymentId()).stream()
                .anyMatch(s -> s.status() == PaymentConfirmationStepStatus.COMPENSATED);
        if (hasCompensatedSibling) {
          stepRepository.updateStatusIf(
              step.stepId(), step.status(), PaymentConfirmationStepStatus.COMPENSATED);
          log.warn(
              "Recovery: step {} has COMPENSATED sibling, marking COMPENSATED and skipping",
              step.stepId());
          continue;
        }

        inventoryService.confirmHold(step.holdId());
        // CAS the step from its observed status to CONFIRMED. If a concurrent
        // saga thread already advanced it, the swap is a benign no-op
        // (confirmHold is idempotent so the reservation is correct either way).
        stepRepository.updateStatusIf(
            step.stepId(), step.status(), PaymentConfirmationStepStatus.CONFIRMED);
      } catch (Exception e) {
        log.warn("Recovery: failed to confirm stale pending step {}", step.stepId(), e);
      }
    }
  }
}
