package com.asoviewclone.commercecore.points.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.asoviewclone.commercecore.points.model.PointReason;
import com.asoviewclone.commercecore.points.repository.PointBalanceRepository;
import com.asoviewclone.commercecore.points.repository.PointLedgerRepository;
import com.asoviewclone.common.error.ValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

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
    // Insert-first ledger gate claims the (reason, orderId) tuple.
    when(ledgerRepo.insertIfMissing(
            any(UUID.class),
            eq(userId),
            eq(100L),
            eq(PointReason.EARN_PURCHASE.name()),
            eq(orderId)))
        .thenReturn(1);
    when(balanceRepo.findCurrentBalance(userId)).thenReturn(Optional.of(0L));
    when(balanceRepo.casBalance(eq(userId), eq(0L), eq(100L))).thenReturn(1);

    service.earn(userId, 100, orderId);

    verify(ledgerRepo)
        .insertIfMissing(
            any(UUID.class),
            eq(userId),
            eq(100L),
            eq(PointReason.EARN_PURCHASE.name()),
            eq(orderId));
    verify(balanceRepo).insertIfMissing(eq(userId), eq(0L));
    verify(balanceRepo).casBalance(eq(userId), eq(0L), eq(100L));
  }

  @Test
  void earn_idempotent_noops() {
    // Insert-first gate reports duplicate (0 rows inserted) → service should
    // return without touching the balance row at all.
    when(ledgerRepo.insertIfMissing(
            any(UUID.class),
            eq(userId),
            anyLong(),
            eq(PointReason.EARN_PURCHASE.name()),
            eq(orderId)))
        .thenReturn(0);

    service.earn(userId, 100, orderId);

    verify(balanceRepo, never()).insertIfMissing(any(), anyLong());
    verify(balanceRepo, never()).casBalance(any(), anyLong(), anyLong());
  }

  @Test
  void burn_insufficientBalance_throws() {
    when(ledgerRepo.insertIfMissing(
            any(UUID.class),
            eq(userId),
            eq(-100L),
            eq(PointReason.BURN_PURCHASE.name()),
            eq(orderId)))
        .thenReturn(1);
    when(balanceRepo.findCurrentBalance(userId)).thenReturn(Optional.of(50L));

    assertThatThrownBy(() -> service.burn(userId, 100, orderId))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void refund_reCreditsBurnedPoints() {
    when(ledgerRepo.insertIfMissing(
            any(UUID.class),
            eq(userId),
            eq(100L),
            eq(PointReason.REFUND_CANCEL.name()),
            eq(orderId)))
        .thenReturn(1);
    when(balanceRepo.findCurrentBalance(userId)).thenReturn(Optional.of(0L));
    when(balanceRepo.casBalance(eq(userId), eq(0L), eq(100L))).thenReturn(1);

    service.refund(userId, 100, orderId);

    verify(balanceRepo).casBalance(eq(userId), eq(0L), eq(100L));
    verify(ledgerRepo)
        .insertIfMissing(
            any(UUID.class),
            eq(userId),
            eq(100L),
            eq(PointReason.REFUND_CANCEL.name()),
            eq(orderId));
  }

  @Test
  void getBalance_noRow_returnsZero() {
    when(balanceRepo.findById(userId)).thenReturn(Optional.empty());
    assertThat(service.getBalance(userId)).isZero();
  }

  // Suppress unused-import warning when the static helper above is unused in some
  // build configurations.
  @SuppressWarnings("unused")
  private static <T> T anyMatcher() {
    return ArgumentMatchers.any();
  }
}
