package com.asoviewclone.commercecore.points.service;

import com.asoviewclone.commercecore.points.model.PointBalance;
import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import com.asoviewclone.commercecore.points.model.PointReason;
import com.asoviewclone.commercecore.points.repository.PointBalanceRepository;
import com.asoviewclone.commercecore.points.repository.PointLedgerRepository;
import com.asoviewclone.common.error.ValidationException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PointServiceImpl implements PointService {

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
    PointBalance balance =
        balanceRepository.findById(userId).orElseGet(() -> new PointBalance(userId));
    long newBalance = balance.getBalance() + (long) sign * amount;
    if (newBalance < 0) {
      throw new ValidationException(
          "Insufficient point balance: have " + balance.getBalance() + ", need " + amount);
    }
    balance.setBalance(newBalance);
    balanceRepository.save(balance);
    ledgerRepository.save(new PointLedgerEntry(userId, (long) sign * amount, reason, orderId));
  }
}
