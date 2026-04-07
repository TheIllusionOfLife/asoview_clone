package com.asoviewclone.commercecore.points.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.points.model.PointBalance;
import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import com.asoviewclone.commercecore.points.model.PointReason;
import com.asoviewclone.commercecore.points.repository.PointBalanceRepository;
import com.asoviewclone.commercecore.points.repository.PointLedgerRepository;
import com.asoviewclone.common.error.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PointServiceImplTest {

  private PointBalanceRepository balanceRepo;
  private PointLedgerRepository ledgerRepo;
  private PointServiceImpl service;

  private final UUID userId = UUID.randomUUID();
  private final String orderId = "order-1";

  @BeforeEach
  void setUp() {
    balanceRepo = mock(PointBalanceRepository.class);
    ledgerRepo = mock(PointLedgerRepository.class);
    service = new PointServiceImpl(balanceRepo, ledgerRepo);
  }

  @Test
  void earn_creditsAndWritesLedger() {
    when(balanceRepo.findById(userId)).thenReturn(Optional.empty());
    when(ledgerRepo.existsByReasonAndOrderId(PointReason.EARN_PURCHASE, orderId)).thenReturn(false);
    when(balanceRepo.save(any(PointBalance.class))).thenAnswer(inv -> inv.getArgument(0));

    service.earn(userId, 100, orderId);

    verify(ledgerRepo).save(any(PointLedgerEntry.class));
    verify(balanceRepo).save(any(PointBalance.class));
  }

  @Test
  void earn_idempotent_noops() {
    when(ledgerRepo.existsByReasonAndOrderId(PointReason.EARN_PURCHASE, orderId)).thenReturn(true);

    service.earn(userId, 100, orderId);

    verify(ledgerRepo, never()).save(any());
    verify(balanceRepo, never()).save(any());
  }

  @Test
  void burn_insufficientBalance_throws() {
    PointBalance b = new PointBalance(userId);
    b.setBalance(50);
    when(balanceRepo.findById(userId)).thenReturn(Optional.of(b));
    when(ledgerRepo.existsByReasonAndOrderId(PointReason.BURN_PURCHASE, orderId)).thenReturn(false);

    assertThatThrownBy(() -> service.burn(userId, 100, orderId))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void refund_reCreditsBurnedPoints() {
    PointBalance b = new PointBalance(userId);
    b.setBalance(0);
    when(balanceRepo.findById(userId)).thenReturn(Optional.of(b));
    when(ledgerRepo.existsByReasonAndOrderId(PointReason.REFUND_CANCEL, orderId)).thenReturn(false);
    when(balanceRepo.save(any(PointBalance.class))).thenAnswer(inv -> inv.getArgument(0));

    service.refund(userId, 100, orderId);

    assertThat(b.getBalance()).isEqualTo(100);
  }

  @Test
  void getBalance_noRow_returnsZero() {
    when(balanceRepo.findById(userId)).thenReturn(Optional.empty());
    assertThat(service.getBalance(userId)).isZero();
  }
}
