package com.asoviewclone.ticketing.repository;

import com.asoviewclone.ticketing.exception.NotScannableException;
import com.asoviewclone.ticketing.exception.TerminalConflictException;
import com.asoviewclone.ticketing.model.RedeemOutcome;
import com.asoviewclone.ticketing.model.RedeemResult;
import com.asoviewclone.ticketing.model.TicketPassStatus;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.Value;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Owns the atomic redeem path: status CAS + entitlement/validity/venue checks + audit INSERT +
 * idempotency key persistence, all inside ONE Spanner read-write transaction. The inner lambda
 * ALWAYS returns a {@link RedeemResult} — never throws — so audit and idempotency mutations always
 * commit alongside the outcome. The outer method maps non-REDEEMED outcomes to exceptions after the
 * transaction commits.
 */
@Repository
public class TicketPassRedeemRepository {

  private static final Logger log = LoggerFactory.getLogger(TicketPassRedeemRepository.class);

  private final DatabaseClient databaseClient;

  public TicketPassRedeemRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  /**
   * @param qrCodePayload the QR payload string scanned at the gate (e.g. "TKT-A1B2C3..."); looked
   *     up against {@code ticket_passes.qr_code_payload}.
   */
  public RedeemResult redeemAtomically(
      String qrCodePayload,
      String scannerUserId,
      String scannerDeviceId,
      Set<String> operatorVenueIds,
      String operatorTenantId,
      String idempotencyKey,
      String sourceIp) {
    RedeemResult result =
        databaseClient
            .readWriteTransaction()
            .run(
                tx ->
                    runRedeem(
                        tx,
                        qrCodePayload,
                        scannerUserId,
                        scannerDeviceId,
                        operatorVenueIds,
                        operatorTenantId,
                        idempotencyKey,
                        sourceIp));
    return dispatchOutcome(result);
  }

  private static RedeemResult dispatchOutcome(RedeemResult result) {
    return switch (result.outcome()) {
      case REDEEMED -> result;
      case NOT_FOUND, TENANT_MISMATCH, VENUE_MISMATCH, ROLE_DENIED, FORMAT_INVALID ->
          throw new NotScannableException("Ticket not valid at this gate");
      case ALREADY_USED ->
          throw new TerminalConflictException(
              result.outcome().name(),
              "Pass already used at "
                  + (result.usedAt() != null ? result.usedAt().toString() : "unknown"));
      case EXPIRED -> throw new TerminalConflictException(result.outcome().name(), "Pass expired");
      case REVOKED -> throw new TerminalConflictException(result.outcome().name(), "Pass revoked");
      case ENTITLEMENT_NOT_ACTIVE ->
          throw new TerminalConflictException(result.outcome().name(), "Entitlement not active");
      case OUTSIDE_VALIDITY_WINDOW ->
          throw new TerminalConflictException(result.outcome().name(), "Outside validity window");
      case RATE_LIMITED, IDEMPOTENCY_REUSED ->
          throw new TerminalConflictException(result.outcome().name(), result.outcome().name());
    };
  }

  private RedeemResult runRedeem(
      TransactionContext tx,
      String qrCodePayload,
      String scannerUserId,
      String scannerDeviceId,
      Set<String> operatorVenueIds,
      String operatorTenantId,
      String idempotencyKey,
      String sourceIp) {

    // 1. Lookup pass + entitlement (may return null row).
    PassRow pass = lookupPass(tx, qrCodePayload);
    String passId = pass != null ? pass.passId : null;
    String passVenueId = pass != null ? pass.venueId : null;

    // 2. Idempotency replay / misuse detection.
    IdemRow idem = lookupIdempotency(tx, idempotencyKey);
    if (idem != null) {
      boolean scannerMismatch = !idem.scannerUserId.equals(scannerUserId);
      boolean passMismatch = (idem.passId == null ? passId != null : !idem.passId.equals(passId));
      if (scannerMismatch || passMismatch) {
        writeAudit(
            tx,
            operatorTenantId,
            passId,
            scannerUserId,
            scannerDeviceId,
            passVenueId,
            RedeemOutcome.ROLE_DENIED,
            sourceIp,
            idempotencyKey);
        return new RedeemResult(passId, null, null, RedeemOutcome.ROLE_DENIED, false);
      }
      // Replay: do NOT re-audit and do NOT re-write idempotency. Return cached outcome.
      return cachedReplay(idem);
    }

    // 3. Fail-closed auth: non-admin redeem path requires tenant + at least one scanner venue.
    if (operatorTenantId == null
        || operatorTenantId.isBlank()
        || operatorVenueIds == null
        || operatorVenueIds.isEmpty()) {
      RedeemOutcome outcome = RedeemOutcome.ROLE_DENIED;
      writeAudit(
          tx,
          operatorTenantId,
          passId,
          scannerUserId,
          scannerDeviceId,
          passVenueId,
          outcome,
          sourceIp,
          idempotencyKey);
      writeIdempotency(tx, idempotencyKey, scannerUserId, passId, outcome);
      return new RedeemResult(passId, null, null, outcome, false);
    }

    if (pass == null) {
      RedeemOutcome outcome = RedeemOutcome.NOT_FOUND;
      writeAudit(
          tx,
          operatorTenantId,
          null,
          scannerUserId,
          scannerDeviceId,
          null,
          outcome,
          sourceIp,
          idempotencyKey);
      writeIdempotency(tx, idempotencyKey, scannerUserId, null, outcome);
      return new RedeemResult(null, null, null, outcome, false);
    }

    // 4. Tenant / venue fail-closed checks. Pre-V6 legacy rows (null pass.tenantId/venueId) are
    // not scannable — backfill is required before scanner rollout.
    if (pass.tenantId == null || !operatorTenantId.equals(pass.tenantId)) {
      RedeemOutcome outcome = RedeemOutcome.TENANT_MISMATCH;
      writeAudit(
          tx,
          operatorTenantId,
          passId,
          scannerUserId,
          scannerDeviceId,
          passVenueId,
          outcome,
          sourceIp,
          idempotencyKey);
      writeIdempotency(tx, idempotencyKey, scannerUserId, passId, outcome);
      return new RedeemResult(passId, null, null, outcome, false);
    }
    if (pass.venueId == null || !operatorVenueIds.contains(pass.venueId)) {
      RedeemOutcome outcome = RedeemOutcome.VENUE_MISMATCH;
      writeAudit(
          tx,
          operatorTenantId,
          passId,
          scannerUserId,
          scannerDeviceId,
          passVenueId,
          outcome,
          sourceIp,
          idempotencyKey);
      writeIdempotency(tx, idempotencyKey, scannerUserId, passId, outcome);
      return new RedeemResult(passId, null, null, outcome, false);
    }

    // 5. Entitlement status / validity window.
    if (pass.entitlementStatus != null && !"ACTIVE".equals(pass.entitlementStatus)) {
      return recordTerminal(
          tx,
          RedeemOutcome.ENTITLEMENT_NOT_ACTIVE,
          pass,
          scannerUserId,
          scannerDeviceId,
          operatorTenantId,
          sourceIp,
          idempotencyKey);
    }
    Instant now = Instant.now();
    if (pass.validFrom != null && now.isBefore(pass.validFrom)) {
      return recordTerminal(
          tx,
          RedeemOutcome.OUTSIDE_VALIDITY_WINDOW,
          pass,
          scannerUserId,
          scannerDeviceId,
          operatorTenantId,
          sourceIp,
          idempotencyKey);
    }
    if (pass.validUntil != null && now.isAfter(pass.validUntil)) {
      return recordTerminal(
          tx,
          RedeemOutcome.OUTSIDE_VALIDITY_WINDOW,
          pass,
          scannerUserId,
          scannerDeviceId,
          operatorTenantId,
          sourceIp,
          idempotencyKey);
    }

    // 6. Pass status dispatch.
    switch (pass.status) {
      case REVOKED:
        return recordTerminal(
            tx,
            RedeemOutcome.REVOKED,
            pass,
            scannerUserId,
            scannerDeviceId,
            operatorTenantId,
            sourceIp,
            idempotencyKey);
      case EXPIRED:
        return recordTerminal(
            tx,
            RedeemOutcome.EXPIRED,
            pass,
            scannerUserId,
            scannerDeviceId,
            operatorTenantId,
            sourceIp,
            idempotencyKey);
      case USED:
        return recordTerminal(
            tx,
            RedeemOutcome.ALREADY_USED,
            pass,
            scannerUserId,
            scannerDeviceId,
            operatorTenantId,
            sourceIp,
            idempotencyKey);
      case VALID:
        tx.buffer(
            Mutation.newUpdateBuilder("ticket_passes")
                .set("ticket_pass_id")
                .to(pass.passId)
                .set("status")
                .to(TicketPassStatus.USED.name())
                .set("used_at")
                .to(Value.COMMIT_TIMESTAMP)
                .build());
        writeAudit(
            tx,
            operatorTenantId,
            pass.passId,
            scannerUserId,
            scannerDeviceId,
            pass.venueId,
            RedeemOutcome.REDEEMED,
            sourceIp,
            idempotencyKey);
        writeIdempotency(tx, idempotencyKey, scannerUserId, pass.passId, RedeemOutcome.REDEEMED);
        return new RedeemResult(
            pass.passId, TicketPassStatus.USED, null, RedeemOutcome.REDEEMED, false);
      default:
        log.error("Unexpected status {} on pass {}", pass.status, pass.passId);
        throw new IllegalStateException("Unexpected status: " + pass.status);
    }
  }

  private static RedeemResult recordTerminal(
      TransactionContext tx,
      RedeemOutcome outcome,
      PassRow pass,
      String scannerUserId,
      String scannerDeviceId,
      String operatorTenantId,
      String sourceIp,
      String idempotencyKey) {
    writeAudit(
        tx,
        operatorTenantId,
        pass.passId,
        scannerUserId,
        scannerDeviceId,
        pass.venueId,
        outcome,
        sourceIp,
        idempotencyKey);
    writeIdempotency(tx, idempotencyKey, scannerUserId, pass.passId, outcome);
    return new RedeemResult(pass.passId, pass.status, pass.usedAt, outcome, false);
  }

  private static RedeemResult cachedReplay(IdemRow idem) {
    RedeemOutcome outcome = idem.outcome;
    TicketPassStatus status = outcome == RedeemOutcome.REDEEMED ? TicketPassStatus.USED : null;
    return new RedeemResult(idem.passId, status, null, outcome, true);
  }

  /**
   * Revoke a VALID pass (admin-only path, called outside the redeem endpoint). Uses a CAS on {@code
   * status == VALID} so an already-used or already-revoked pass throws {@link
   * TerminalConflictException}.
   */
  public void revokeAtomically(String passId) {
    try {
      databaseClient
          .readWriteTransaction()
          .run(
              tx -> {
                Statement stmt =
                    Statement.newBuilder(
                            "SELECT status FROM ticket_passes WHERE ticket_pass_id = @id")
                        .bind("id")
                        .to(passId)
                        .build();
                String status = null;
                try (ResultSet rs = tx.executeQuery(stmt)) {
                  if (rs.next()) {
                    status = rs.getString("status");
                  }
                }
                if (status == null) {
                  throw new NotScannableException("Ticket not found");
                }
                if (!TicketPassStatus.VALID.name().equals(status)) {
                  throw new TerminalConflictException(
                      "REVOKE_REJECTED", "Cannot revoke pass in status " + status);
                }
                tx.buffer(
                    Mutation.newUpdateBuilder("ticket_passes")
                        .set("ticket_pass_id")
                        .to(passId)
                        .set("status")
                        .to(TicketPassStatus.REVOKED.name())
                        .build());
                return null;
              });
    } catch (com.google.cloud.spanner.SpannerException e) {
      Throwable cause = e.getCause();
      while (cause != null) {
        if (cause instanceof NotScannableException nse) {
          throw nse;
        }
        if (cause instanceof TerminalConflictException tce) {
          throw tce;
        }
        cause = cause.getCause();
      }
      throw e;
    }
  }

  /** Records a session revocation (admin helper). */
  public void revokeSession(String userId, String sessionId) {
    databaseClient.write(
        java.util.List.of(
            Mutation.newInsertOrUpdateBuilder("revoked_sessions")
                .set("user_id")
                .to(userId)
                .set("session_id")
                .to(sessionId)
                .set("revoked_at")
                .to(Value.COMMIT_TIMESTAMP)
                .build()));
  }

  // ---- Private helpers ----

  private static PassRow lookupPass(TransactionContext tx, String qrCodePayload) {
    Statement passStmt =
        Statement.newBuilder(
                "SELECT tp.ticket_pass_id, tp.status, tp.used_at, tp.venue_id,"
                    + " tp.tenant_id, e.status AS e_status, e.valid_from, e.valid_until"
                    + " FROM ticket_passes tp"
                    + " JOIN entitlements e ON tp.entitlement_id = e.entitlement_id"
                    + " WHERE tp.qr_code_payload = @qr")
            .bind("qr")
            .to(qrCodePayload)
            .build();
    try (ResultSet rs = tx.executeQuery(passStmt)) {
      if (!rs.next()) {
        return null;
      }
      PassRow p = new PassRow();
      p.passId = rs.getString("ticket_pass_id");
      p.status = TicketPassStatus.valueOf(rs.getString("status"));
      p.usedAt =
          rs.isNull("used_at") ? null : rs.getTimestamp("used_at").toSqlTimestamp().toInstant();
      p.venueId = rs.isNull("venue_id") ? null : rs.getString("venue_id");
      p.tenantId = rs.isNull("tenant_id") ? null : rs.getString("tenant_id");
      p.entitlementStatus = rs.getString("e_status");
      p.validFrom =
          rs.isNull("valid_from")
              ? null
              : rs.getTimestamp("valid_from").toSqlTimestamp().toInstant();
      p.validUntil =
          rs.isNull("valid_until")
              ? null
              : rs.getTimestamp("valid_until").toSqlTimestamp().toInstant();
      return p;
    }
  }

  private static IdemRow lookupIdempotency(TransactionContext tx, String key) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT scanner_user_id, ticket_pass_id, outcome FROM"
                    + " ticket_redeem_idempotency WHERE idempotency_key = @key")
            .bind("key")
            .to(key)
            .build();
    try (ResultSet rs = tx.executeQuery(stmt)) {
      if (!rs.next()) {
        return null;
      }
      IdemRow row = new IdemRow();
      row.scannerUserId = rs.getString("scanner_user_id");
      row.passId = rs.isNull("ticket_pass_id") ? null : rs.getString("ticket_pass_id");
      row.outcome = RedeemOutcome.valueOf(rs.getString("outcome"));
      return row;
    }
  }

  private static void writeAudit(
      TransactionContext tx,
      String tenantId,
      String passId,
      String scannerUserId,
      String scannerDeviceId,
      String venueId,
      RedeemOutcome outcome,
      String sourceIp,
      String idempotencyKey) {
    Mutation.WriteBuilder b =
        Mutation.newInsertBuilder("scan_audit_log")
            .set("scan_id")
            .to(UUID.randomUUID().toString())
            .set("tenant_id")
            .to(tenantId != null ? tenantId : "")
            .set("scanner_user_id")
            .to(scannerUserId != null ? scannerUserId : "")
            .set("outcome")
            .to(outcome.name())
            .set("scanned_at")
            .to(Value.COMMIT_TIMESTAMP);
    if (passId != null) {
      b.set("ticket_pass_id").to(passId);
    }
    if (scannerDeviceId != null) {
      b.set("scanner_device_id").to(scannerDeviceId);
    }
    if (venueId != null) {
      b.set("venue_id").to(venueId);
    }
    if (sourceIp != null) {
      b.set("source_ip").to(sourceIp);
    }
    if (idempotencyKey != null) {
      b.set("idempotency_key").to(idempotencyKey);
    }
    tx.buffer(b.build());
  }

  private static void writeIdempotency(
      TransactionContext tx,
      String key,
      String scannerUserId,
      String passId,
      RedeemOutcome outcome) {
    Mutation.WriteBuilder b =
        Mutation.newInsertBuilder("ticket_redeem_idempotency")
            .set("idempotency_key")
            .to(key)
            .set("scanner_user_id")
            .to(scannerUserId)
            .set("outcome")
            .to(outcome.name())
            .set("response_body_hash")
            .to(outcome.name()) // coarse body hash for now
            .set("created_at")
            .to(Value.COMMIT_TIMESTAMP);
    if (passId != null) {
      b.set("ticket_pass_id").to(passId);
    }
    tx.buffer(b.build());
  }

  private static final class PassRow {
    String passId;
    TicketPassStatus status;
    Instant usedAt;
    String venueId;
    String tenantId;
    String entitlementStatus;
    Instant validFrom;
    Instant validUntil;
  }

  private static final class IdemRow {
    String scannerUserId;
    String passId;
    RedeemOutcome outcome;
  }
}
