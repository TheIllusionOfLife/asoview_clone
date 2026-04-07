package com.asoviewclone.commercecore.points.repository;

import com.asoviewclone.commercecore.points.model.PointLedgerEntry;
import com.asoviewclone.commercecore.points.model.PointReason;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointLedgerRepository extends JpaRepository<PointLedgerEntry, UUID> {

  boolean existsByReasonAndOrderId(PointReason reason, String orderId);

  /**
   * Insert a ledger row, returning the number of rows inserted (1 = first to claim this {@code
   * (reason, order_id)} tuple, 0 = a concurrent winner already inserted it). Backed by Postgres
   * {@code ON CONFLICT DO NOTHING} against the partial unique index on {@code (reason, order_id)
   * WHERE order_id IS NOT NULL} from V12.
   *
   * <p>{@code PointServiceImpl} uses this as the atomic uniqueness gate BEFORE touching the balance
   * row, so a concurrent duplicate cannot land mid-transaction and force a doomed-tx compensation
   * path. (PR #21 review C6 follow-up.)
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO point_ledger(id, user_id, delta, reason, order_id, created_at)"
              + " VALUES(:id, :userId, :delta, :reason, :orderId, CURRENT_TIMESTAMP)"
              + " ON CONFLICT (reason, order_id) WHERE order_id IS NOT NULL DO NOTHING",
      nativeQuery = true)
  int insertIfMissing(
      @Param("id") UUID id,
      @Param("userId") UUID userId,
      @Param("delta") long delta,
      @Param("reason") String reason,
      @Param("orderId") String orderId);
}
