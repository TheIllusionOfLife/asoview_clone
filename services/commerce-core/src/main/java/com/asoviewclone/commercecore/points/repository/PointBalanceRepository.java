package com.asoviewclone.commercecore.points.repository;

import com.asoviewclone.commercecore.points.model.PointBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointBalanceRepository extends JpaRepository<PointBalance, UUID> {

  /**
   * Compare-and-swap the balance from {@code expected} to {@code newBalance}. Returns the number of
   * rows updated (1 = won the race, 0 = concurrent writer changed the row first). Used by {@code
   * PointServiceImpl.apply} to avoid the read-modify-write race documented in PR #21 review C5 from
   * CodeRabbit.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE PointBalance b SET b.balance = :newBalance, b.updatedAt = CURRENT_TIMESTAMP"
          + " WHERE b.userId = :userId AND b.balance = :expected")
  int casBalance(
      @Param("userId") UUID userId,
      @Param("expected") long expected,
      @Param("newBalance") long newBalance);

  /**
   * Returns just the {@code balance} column for the given user, bypassing the JPA persistence
   * context entirely. Used by the CAS retry loop in {@code PointServiceImpl} so each iteration sees
   * a fresh value rather than the cached entity from the previous attempt. Returns {@link
   * Optional#empty()} if no row exists.
   */
  @Query("SELECT b.balance FROM PointBalance b WHERE b.userId = :userId")
  Optional<Long> findCurrentBalance(@Param("userId") UUID userId);

  /**
   * Insert a fresh balance row for a user that has none. Idempotent against the unique PK so
   * concurrent inserts simply lose to {@link
   * org.springframework.dao.DataIntegrityViolationException} which the caller treats as "row
   * exists, retry CAS".
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO point_balances(user_id, balance, updated_at)"
              + " VALUES(:userId, :balance, CURRENT_TIMESTAMP)"
              + " ON CONFLICT (user_id) DO NOTHING",
      nativeQuery = true)
  int insertIfMissing(@Param("userId") UUID userId, @Param("balance") long balance);
}
