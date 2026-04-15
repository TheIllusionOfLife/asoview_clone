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
import com.google.cloud.spanner.Value;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Owns the atomic redeem path: status CAS + entitlement/validity/venue checks + audit INSERT +
 * idempotency key persistence, all inside ONE Spanner read-write transaction. See PR 5a plan for
 * the threat model each branch guards against.
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
    try {
      return redeemAtomicallyInner(
          qrCodePayload,
          scannerUserId,
          scannerDeviceId,
          operatorVenueIds,
          operatorTenantId,
          idempotencyKey,
          sourceIp);
    } catch (com.google.cloud.spanner.SpannerException e) {
      // Spanner wraps all exceptions thrown inside readWriteTransaction.run(). Surface the
      // original NotScannable/TerminalConflict so the controller's @ExceptionHandler sees them.
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

  private RedeemResult redeemAtomicallyInner(
      String qrCodePayload,
      String scannerUserId,
      String scannerDeviceId,
      Set<String> operatorVenueIds,
      String operatorTenantId,
      String idempotencyKey,
      String sourceIp) {
    return databaseClient
        .readWriteTransaction()
        .run(
            tx -> {
              // 1. Idempotency replay check
              Statement idemStmt =
                  Statement.newBuilder(
                          "SELECT scanner_user_id, ticket_pass_id, outcome FROM"
                              + " ticket_redeem_idempotency WHERE idempotency_key = @key")
                      .bind("key")
                      .to(idempotencyKey)
                      .build();
              try (ResultSet rs = tx.executeQuery(idemStmt)) {
                if (rs.next()) {
                  String existingScanner = rs.getString("scanner_user_id");
                  String existingPass =
                      rs.isNull("ticket_pass_id") ? null : rs.getString("ticket_pass_id");
                  String existingOutcome = rs.getString("outcome");
                  if (!existingScanner.equals(scannerUserId)) {
                    writeAudit(
                        tx,
                        operatorTenantId,
                        existingPass,
                        scannerUserId,
                        scannerDeviceId,
                        null,
                        RedeemOutcome.ROLE_DENIED,
                        sourceIp,
                        idempotencyKey);
                    throw new NotScannableException("Idempotency key reused by different scanner");
                  }
                  // Same scanner — replay cached response
                  return replayExistingOutcome(tx, existingPass, existingOutcome);
                }
              }

              // 2. Lookup pass + entitlement joined (lookup key is qr_code_payload)
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

              String passId = null;
              String passTenantId = null;
              String passVenueId = null;
              TicketPassStatus passStatus = null;
              Instant existingUsedAt = null;
              String entitlementStatus = null;
              Instant validFrom = null;
              Instant validUntil = null;
              boolean found = false;
              try (ResultSet rs = tx.executeQuery(passStmt)) {
                if (rs.next()) {
                  found = true;
                  passId = rs.getString("ticket_pass_id");
                  passStatus = TicketPassStatus.valueOf(rs.getString("status"));
                  if (!rs.isNull("used_at")) {
                    existingUsedAt = rs.getTimestamp("used_at").toSqlTimestamp().toInstant();
                  }
                  if (!rs.isNull("venue_id")) {
                    passVenueId = rs.getString("venue_id");
                  }
                  if (!rs.isNull("tenant_id")) {
                    passTenantId = rs.getString("tenant_id");
                  }
                  entitlementStatus = rs.getString("e_status");
                  if (!rs.isNull("valid_from")) {
                    validFrom = rs.getTimestamp("valid_from").toSqlTimestamp().toInstant();
                  }
                  if (!rs.isNull("valid_until")) {
                    validUntil = rs.getTimestamp("valid_until").toSqlTimestamp().toInstant();
                  }
                }
              }

              if (!found) {
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
                throw new NotScannableException("Ticket not found");
              }

              // 3. Tenant / venue checks (both masquerade as 404)
              if (operatorTenantId != null
                  && passTenantId != null
                  && !operatorTenantId.equals(passTenantId)) {
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
                throw new NotScannableException("Tenant mismatch");
              }

              if (passVenueId != null
                  && operatorVenueIds != null
                  && !operatorVenueIds.isEmpty()
                  && !operatorVenueIds.contains(passVenueId)) {
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
                throw new NotScannableException("Venue mismatch");
              }

              // 4. Entitlement status / validity window (only if we have entitlement info)
              if (entitlementStatus != null && !"ACTIVE".equals(entitlementStatus)) {
                RedeemOutcome outcome = RedeemOutcome.ENTITLEMENT_NOT_ACTIVE;
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
                throw new TerminalConflictException(outcome.name(), "Entitlement not active");
              }
              Instant now = Instant.now();
              if (validFrom != null && now.isBefore(validFrom)) {
                RedeemOutcome outcome = RedeemOutcome.OUTSIDE_VALIDITY_WINDOW;
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
                throw new TerminalConflictException(outcome.name(), "Before valid_from");
              }
              if (validUntil != null && now.isAfter(validUntil)) {
                RedeemOutcome outcome = RedeemOutcome.OUTSIDE_VALIDITY_WINDOW;
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
                throw new TerminalConflictException(outcome.name(), "After valid_until");
              }

              // 5. Pass status dispatch
              switch (passStatus) {
                case REVOKED -> {
                  RedeemOutcome outcome = RedeemOutcome.REVOKED;
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
                  throw new TerminalConflictException(outcome.name(), "Pass revoked");
                }
                case EXPIRED -> {
                  RedeemOutcome outcome = RedeemOutcome.EXPIRED;
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
                  throw new TerminalConflictException(outcome.name(), "Pass expired");
                }
                case USED -> {
                  RedeemOutcome outcome = RedeemOutcome.ALREADY_USED;
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
                  // Return used_at in the exception message for the controller to surface
                  throw new TerminalConflictException(
                      outcome.name(),
                      "Pass already used at "
                          + (existingUsedAt != null ? existingUsedAt.toString() : "unknown"));
                }
                case VALID -> {
                  // CAS update + audit + idempotency — atomic
                  tx.buffer(
                      Mutation.newUpdateBuilder("ticket_passes")
                          .set("ticket_pass_id")
                          .to(passId)
                          .set("status")
                          .to(TicketPassStatus.USED.name())
                          .set("used_at")
                          .to(Value.COMMIT_TIMESTAMP)
                          .build());
                  RedeemOutcome outcome = RedeemOutcome.REDEEMED;
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
                  return new RedeemResult(passId, TicketPassStatus.USED, null, outcome, false);
                }
                default -> {
                  log.error("Unexpected status {} on pass {}", passStatus, passId);
                  throw new IllegalStateException("Unexpected status: " + passStatus);
                }
              }
            });
  }

  /**
   * Revoke a VALID pass (admin-only path, called outside the redeem endpoint). Uses a CAS on {@code
   * status == VALID} so an already-used or already-revoked pass throws {@link
   * TerminalConflictException}.
   */
  public void revokeAtomically(String passId) {
    try {
      revokeAtomicallyInner(passId);
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

  private void revokeAtomicallyInner(String passId) {
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
  }

  /** Looks up whether the given session was revoked. Used by the Firebase filter. */
  public boolean isSessionRevoked(String userId, String sessionId) {
    if (userId == null || sessionId == null) {
      return false;
    }
    Statement stmt =
        Statement.newBuilder(
                "SELECT 1 FROM revoked_sessions WHERE user_id = @uid AND session_id = @sid")
            .bind("uid")
            .to(userId)
            .bind("sid")
            .to(sessionId)
            .build();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      return rs.next();
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

  private RedeemResult replayExistingOutcome(
      com.google.cloud.spanner.TransactionContext tx, String passId, String outcome) {
    RedeemOutcome ro = RedeemOutcome.valueOf(outcome);
    return switch (ro) {
      case REDEEMED -> new RedeemResult(passId, TicketPassStatus.USED, null, ro, true);
      case NOT_FOUND, TENANT_MISMATCH, VENUE_MISMATCH, ROLE_DENIED, FORMAT_INVALID ->
          throw new NotScannableException("Replay: " + outcome);
      case ALREADY_USED,
          EXPIRED,
          REVOKED,
          ENTITLEMENT_NOT_ACTIVE,
          OUTSIDE_VALIDITY_WINDOW,
          RATE_LIMITED,
          IDEMPOTENCY_REUSED ->
          throw new TerminalConflictException(outcome, "Replay: " + outcome);
    };
  }

  private static void writeAudit(
      com.google.cloud.spanner.TransactionContext tx,
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
      com.google.cloud.spanner.TransactionContext tx,
      String key,
      String scannerUserId,
      String passId,
      RedeemOutcome outcome) {
    Mutation.WriteBuilder b =
        Mutation.newInsertOrUpdateBuilder("ticket_redeem_idempotency")
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
}
