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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
  @Transactional(readOnly = true)
  public Page<PointLedgerEntry> getLedger(UUID userId, Pageable pageable) {
    return ledgerRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
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
    long delta = (long) sign * amount;

    // INSERT-FIRST IDEMPOTENCY GATE.
    //
    // The previous implementation used existsByReasonAndOrderId then
    // ledgerRepository.save(...), then if the save lost the unique-constraint
    // race we'd compensate the balance back. That compensation ran inside a
    // transaction Spring had already flipped to rollback-only by the
    // DataIntegrityViolationException, so the compensation never persisted —
    // PR #21 review C6 caught the TOCTOU gap, follow-up review caught that
    // the compensation path was doomed.
    //
    // The clean shape is: try the ledger insert FIRST via INSERT ... ON
    // CONFLICT DO NOTHING. If it returns 0, a concurrent winner already
    // applied this (reason, orderId) — return idempotent no-op without
    // ever touching the balance row. If it returns 1, we hold the
    // uniqueness gate and can safely CAS the balance.
    if (orderId != null) {
      int inserted =
          ledgerRepository.insertIfMissing(
              UUID.randomUUID(), userId, delta, reason.name(), orderId);
      if (inserted == 0) {
        return;
      }
    } else {
      // Ad-hoc credits/debits with no orderId have no idempotency key, so
      // fall back to a plain insert. The caller is responsible for guarding
      // duplicate-fire (these paths are admin/test only today).
      ledgerRepository.save(new PointLedgerEntry(userId, delta, reason, null));
    }

    // Now CAS the balance. Retry on contention. If we exhaust retries we
    // throw IllegalStateException — because the ledger insert and the balance
    // CAS run inside the same @Transactional method, throwing rolls the
    // ENTIRE transaction back (including the ledger row we just inserted),
    // so under normal ACID operation the caller sees a clean retryable
    // failure with no divergence. The phrase "reconciliation job" elsewhere
    // in this codebase only matters for cross-store divergence (Spanner
    // orders vs Postgres ledger) under distributed-system failures, not for
    // this single-store JPA path.
    balanceRepository.insertIfMissing(userId, 0L);
    boolean swapped = false;
    for (int attempt = 0; attempt < CAS_MAX_ATTEMPTS; attempt++) {
      long observedBalance = balanceRepository.findCurrentBalance(userId).orElse(0L);
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
  }
}
