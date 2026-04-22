package com.asoviewclone.commercecore.payments.repository;

import com.asoviewclone.commercecore.payments.model.Payment;
import com.asoviewclone.commercecore.payments.model.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  Optional<Payment> findByOrderId(String orderId);

  /**
   * All payments for an order, newest first. FAILED + CANCELLED rows can accumulate alongside an
   * active row, so callers that need "the active payment, if any" must filter the result rather
   * than relying on {@link #findByOrderId} (which throws {@code
   * IncorrectResultSizeDataAccessException} on &gt;1 row). Explicit JPQL rather than a derived
   * method name because the Spring Data parser did not reliably traverse the embedded {@code
   * AuditFields audit -> createdAt} path in this codebase (dev queries silently returned empty even
   * when rows existed), which broke the dev mark-paid endpoint.
   */
  @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId ORDER BY p.audit.createdAt DESC")
  List<Payment> findAllForOrderOrderByCreatedAtDesc(@Param("orderId") String orderId);

  /**
   * Looks up a payment by the provider-issued identifier stored in {@code provider_payment_id}
   * (e.g., a Stripe {@code pi_...}). A partial unique index (V6) guarantees at most one row per
   * non-null provider id.
   */
  Optional<Payment> findByProviderPaymentId(String providerPaymentId);

  /**
   * Bounded lookup by status. Used by the reconciliation job to sweep PROCESSING payments in
   * batches without loading the entire table into memory.
   */
  List<Payment> findByStatus(PaymentStatus status, Pageable pageable);

  /**
   * Compare-and-swap status update. Returns the number of rows updated (0 or 1). Used by the
   * reconciliation job to atomically promote PROCESSING payments to SUCCEEDED/FAILED without racing
   * with concurrent {@code confirmPayment} writes.
   */
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "UPDATE Payment p SET p.status = :newStatus "
          + "WHERE p.paymentId = :paymentId AND p.status = :expected")
  int updateStatusIf(
      @Param("paymentId") UUID paymentId,
      @Param("expected") PaymentStatus expected,
      @Param("newStatus") PaymentStatus newStatus);
}
