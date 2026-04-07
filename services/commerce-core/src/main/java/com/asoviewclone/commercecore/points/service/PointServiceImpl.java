package com.asoviewclone.commercecore.points.service;

import com.asoviewclone.commercecore.points.model.PointBalance;
import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import com.asoviewclone.commercecore.points.model.PointReason;
import com.asoviewclone.commercecore.points.repository.PointBalanceRepository;
import com.asoviewclone.commercecore.points.repository.PointLedgerRepository;
import com.asoviewclone.common.error.ValidationException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PointServiceImpl implements PointService {

  private static final Logger log = LoggerFactory.getLogger(PointServiceImpl.class);
  private static final int CAS_MAX_ATTEMPTS = 5;

  private final PointBalanceRepository balanceRepository;
  private final PointLedgerRepository ledgerRepository;

  public PointServiceImpl(
      PointBalanceRepository balanceRepository, PointLedgerRepository ledgerRepository) {
    this.balanceRepository = balanceRepository;
    this.ledgerRepository = ledgerRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public long getBalance(UUID userId) {
    return balanceRepository.findById(userId).map(PointBalance::getBalance).orElse(0L);
  }

  @Override
  public void earn(UUID userId, long amount, String orderId) {
    apply(userId, amount, PointReason.EARN_PURCHASE, orderId, +1);
  }

  @Override
  public void burn(UUID userId, long amount, String orderId) {
    apply(userId, amount, PointReason.BURN_PURCHASE, orderId, -1);
  }

  @Override
  public void refund(UUID userId, long amount, String orderId) {
    apply(userId, amount, PointReason.REFUND_CANCEL, orderId, +1);
  }

  private void apply(UUID userId, long amount, PointReason reason, String orderId, int sign) {
    if (amount < 0) {
      throw new ValidationException("Point amount must be non-negative");
    }
    if (amount == 0) {
      return;
    }
    if (orderId != null && ledgerRepository.existsByReasonAndOrderId(reason, orderId)) {
      // Already applied; idempotent no-op.
      return;
    }

    // CAS the balance row to avoid the read-modify-write race that
    // lost-writes between concurrent earn/burn calls (PR #21 review C5).
    // Ensure the row exists first via INSERT ... ON CONFLICT DO NOTHING.
    balanceRepository.insertIfMissing(userId, 0L);
    long delta = (long) sign * amount;
    boolean swapped = false;
    long observedBalance = 0L;
    for (int attempt = 0; attempt < CAS_MAX_ATTEMPTS; attempt++) {
      observedBalance = balanceRepository.findById(userId).map(PointBalance::getBalance).orElse(0L);
      long newBalance = observedBalance + delta;
      if (newBalance < 0) {
        throw new ValidationException(
            "Insufficient point balance: have " + observedBalance + ", need " + amount);
      }
      int rows = balanceRepository.casBalance(userId, observedBalance, newBalance);
      if (rows == 1) {
        swapped = true;
        break;
      }
      log.debug("PointBalance CAS lost race for user {} attempt {}; retrying", userId, attempt + 1);
    }
    if (!swapped) {
      throw new IllegalStateException(
          "PointBalance CAS exhausted retries for user " + userId + " (concurrent contention)");
    }

    // Insert the ledger entry. The unique(reason, order_id) partial index
    // closes the TOCTOU gap from the existsByReasonAndOrderId check above:
    // a concurrent winner causes a DataIntegrityViolationException, which
    // we treat as idempotent success since the balance update we already
    // committed reflects our delta — except now we'd have applied it twice.
    // The fix is to compensate the balance back if the ledger insert loses
    // the race so the net effect is exactly one application of the delta.
    // (PR #21 review C6 from CodeRabbit.)
    try {
      ledgerRepository.save(new PointLedgerEntry(userId, delta, reason, orderId));
    } catch (DataIntegrityViolationException dup) {
      // Concurrent winner already applied this (reason, orderId). Reverse our
      // balance update so the net effect is the single committed delta.
      log.info(
          "PointLedger duplicate (reason={}, orderId={}, user={}); reversing our balance delta",
          reason,
          orderId,
          userId);
      for (int attempt = 0; attempt < CAS_MAX_ATTEMPTS; attempt++) {
        long currentBalance =
            balanceRepository.findById(userId).map(PointBalance::getBalance).orElse(0L);
        long reversed = currentBalance - delta;
        if (reversed < 0) {
          // Cannot reverse without going negative; the concurrent winner has
          // already drained our delta in another direction. Log and accept
          // the divergence — a reconciliation job will detect and repair.
          log.error(
              "Cannot reverse PointBalance delta for user {} (reversed={}); reconciliation needed",
              userId,
              reversed);
          break;
        }
        if (balanceRepository.casBalance(userId, currentBalance, reversed) == 1) {
          break;
        }
      }
    }
  }
}
