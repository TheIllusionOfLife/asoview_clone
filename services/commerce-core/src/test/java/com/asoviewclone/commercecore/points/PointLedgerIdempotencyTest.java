package com.asoviewclone.commercecore.points;

import static org.assertj.core.api.Assertions.assertThat;

import com.asoviewclone.commercecore.identity.model.User;
import com.asoviewclone.commercecore.identity.repository.UserRepository;
import com.asoviewclone.commercecore.points.model.PointReason;
import com.asoviewclone.commercecore.points.repository.PointBalanceRepository;
import com.asoviewclone.commercecore.points.repository.PointLedgerRepository;
import com.asoviewclone.commercecore.points.service.PointService;
import com.asoviewclone.commercecore.testutil.PostgresContainerConfig;
import com.asoviewclone.commercecore.testutil.RedisContainerConfig;
import com.asoviewclone.commercecore.testutil.SpannerEmulatorConfig;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pitfall 12 (PR #21): the points ledger uses an INSERT-FIRST idempotency gate (ledger row insert
 * via {@code ON CONFLICT DO NOTHING} BEFORE the balance CAS) so a concurrent duplicate {@code
 * burn(...)} cannot land twice. Regression of this rule would re-introduce the "exists-then-save"
 * TOCTOU and a doomed-tx compensation path.
 *
 * <p>This test fires N concurrent {@link PointService#burn} calls with the same {@code (reason,
 * orderId)} tuple, then asserts:
 *
 * <ul>
 *   <li>exactly one BURN_PURCHASE ledger row exists for that order id, and
 *   <li>the balance reflects exactly one burn (initial − amount), not N burns.
 * </ul>
 *
 * <p>If anyone reverts {@code PointServiceImpl.apply} to existsByReasonAndOrderId-then-save, this
 * test fails on the ledger count assertion and on the balance assertion.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresContainerConfig.class, RedisContainerConfig.class, SpannerEmulatorConfig.class})
class PointLedgerIdempotencyTest {

  @Autowired private PointService pointService;
  @Autowired private PointBalanceRepository balanceRepository;
  @Autowired private PointLedgerRepository ledgerRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void concurrentBurnsWithSameOrderIdInsertExactlyOneLedgerRow() throws InterruptedException {
    // point_ledger has a FK to users; seed a real user row.
    User user =
        userRepository.saveAndFlush(
            new User("firebase-" + UUID.randomUUID(), "u@example.com", "Test User"));
    UUID userId = user.getId();
    String orderId = "order-" + UUID.randomUUID();
    long initialEarn = 1_000L;
    long burnAmount = 100L;
    int threads = 8;

    // Seed an EARN_PURCHASE so the burn won't fail on the balance check.
    pointService.earn(userId, initialEarn, "seed-" + UUID.randomUUID());

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);
    AtomicInteger errors = new AtomicInteger();

    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            try {
              start.await();
              pointService.burn(userId, burnAmount, orderId);
            } catch (Exception ex) {
              errors.incrementAndGet();
            } finally {
              done.countDown();
            }
          });
    }
    start.countDown();
    boolean finished = done.await(20, TimeUnit.SECONDS);
    pool.shutdownNow();

    assertThat(finished).as("all threads finished").isTrue();
    // Some threads may legitimately throw on doomed-tx retries; the
    // load-bearing assertion is the row + balance count below.

    // Exactly one ledger row for the (BURN_PURCHASE, orderId) tuple.
    // Using count() not exists(): exists() returns true for 1..N rows, which
    // would silently miss a regression where insert-first idempotency is
    // reverted to exists-then-save TOCTOU and two rows land.
    long burnCount = ledgerRepository.countByReasonAndOrderId(PointReason.BURN_PURCHASE, orderId);
    assertThat(burnCount)
        .as("exactly one burn ledger row must exist, not N (errors=%d)", errors.get())
        .isEqualTo(1L);

    // Balance reflects exactly one burn, not N. (initialEarn − burnAmount).
    long balance = balanceRepository.findCurrentBalance(userId).orElse(-1L);
    assertThat(balance)
        .as("balance reflects exactly one burn, not N (errors=%d)", errors.get())
        .isEqualTo(initialEarn - burnAmount);
  }
}
