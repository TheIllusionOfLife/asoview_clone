package com.asoviewclone.commercecore.points.discount;

import com.asoviewclone.commercecore.orders.repository.OrderRepository;
import com.asoviewclone.commercecore.points.repository.PointLedgerRepository;
import com.asoviewclone.common.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps {@code order_discounts} rows whose corresponding Spanner order does not exist (or no
 * longer exists) and refunds the associated points-burn.
 *
 * <p>This is a defensive recovery for the cross-store crash window in {@code
 * OrderServiceImpl.createOrder}: the points burn commits to Postgres BEFORE the order is written to
 * Spanner. If the JVM dies in between, the burn is stranded against an order id that never landed
 * in Spanner. The synchronous catch path inside createOrder handles the RuntimeException case
 * (refund + StrandedPointsBurnException), but a hard process crash bypasses every catch — only this
 * job recovers it.
 *
 * <p>The sweep waits for a discount row to be at least {@code orphan-grace-minutes} old (default 10
 * min) before considering it a candidate, so a discount inserted moments before the order write is
 * not falsely flagged as orphaned. Each candidate is checked against Spanner via {@link
 * OrderRepository#findById(String)}; a {@link NotFoundException} confirms the orphan and triggers
 * the refund. Idempotent: refundForCancelledOrder deletes the row, and if the refund lost the race
 * to a normal cancel-refund the row is already gone.
 *
 * <p>Codex review of PR #21 flagged the missing recovery path; full outbox/saga refactor remains a
 * follow-up but this job prevents permanent points loss in the crash-mid-method case.
 */
@Component
public class OrphanedDiscountReconciliationJob {

  private static final Logger log =
      LoggerFactory.getLogger(OrphanedDiscountReconciliationJob.class);

  private final OrderDiscountRepository discountRepository;
  private final OrderDiscountService discountService;
  private final OrderRepository orderRepository;
  private final PointLedgerRepository ledgerRepository;
  private final long orphanGraceMinutes;

  @Autowired
  public OrphanedDiscountReconciliationJob(
      OrderDiscountRepository discountRepository,
      OrderDiscountService discountService,
      OrderRepository orderRepository,
      PointLedgerRepository ledgerRepository) {
    this(discountRepository, discountService, orderRepository, ledgerRepository, 10L);
  }

  // Visible-for-testing constructor lets the integration test override the
  // grace period without setting a Spring property.
  OrphanedDiscountReconciliationJob(
      OrderDiscountRepository discountRepository,
      OrderDiscountService discountService,
      OrderRepository orderRepository,
      PointLedgerRepository ledgerRepository,
      long orphanGraceMinutes) {
    this.discountRepository = discountRepository;
    this.discountService = discountService;
    this.orderRepository = orderRepository;
    this.ledgerRepository = ledgerRepository;
    this.orphanGraceMinutes = orphanGraceMinutes;
  }

  /** Runs every 5 minutes. Conservative cadence — the same as PaymentReconciliationJob. */
  @Scheduled(fixedDelay = 300_000L)
  public void reconcileOrphanedDiscounts() {
    Instant threshold = Instant.now().minusSeconds(orphanGraceMinutes * 60);
    List<OrderDiscount> candidates = discountRepository.findOlderThan(threshold);
    int repaired = 0;
    for (OrderDiscount d : candidates) {
      try {
        // Probe Spanner. If the order exists this is a normal in-progress
        // discount and we leave it alone.
        orderRepository.findById(d.getOrderId());
      } catch (NotFoundException nfe) {
        // Spanner says the order does not exist. The points-burn is stranded.
        // Look up the user_id from the BURN_PURCHASE ledger entry (the
        // discount table doesn't carry user_id), then refund.
        Optional<UUID> userId = ledgerRepository.findBurnUserId(d.getOrderId());
        if (userId.isEmpty()) {
          log.error(
              "Orphaned discount order_id={} has no matching BURN_PURCHASE ledger entry; manual repair needed",
              d.getOrderId());
          continue;
        }
        try {
          discountService.refundForCancelledOrder(d.getOrderId(), userId.get());
          repaired++;
          log.warn(
              "Reconciled orphaned points-burn: order_id={} user={} amount={}",
              d.getOrderId(),
              userId.get(),
              d.getAmountJpy());
        } catch (Exception ex) {
          log.error(
              "Failed to refund orphaned discount order_id={} user={}; will retry next sweep",
              d.getOrderId(),
              userId.get(),
              ex);
        }
      } catch (Exception ex) {
        log.warn(
            "Reconciliation probe failed for discount order_id={}; will retry", d.getOrderId(), ex);
      }
    }
    if (repaired > 0) {
      log.info("Orphaned-discount reconciliation pass repaired {} rows", repaired);
    }
  }
}
