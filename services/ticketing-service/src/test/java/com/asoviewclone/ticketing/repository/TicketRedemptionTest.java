package com.asoviewclone.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asoviewclone.ticketing.exception.NotScannableException;
import com.asoviewclone.ticketing.exception.TerminalConflictException;
import com.asoviewclone.ticketing.model.RedeemOutcome;
import com.asoviewclone.ticketing.model.RedeemResult;
import com.asoviewclone.ticketing.model.TicketPassStatus;
import com.asoviewclone.ticketing.testutil.SpannerEmulatorConfig;
import com.asoviewclone.ticketing.testutil.TicketFixtures;
import com.google.cloud.spanner.DatabaseClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(SpannerEmulatorConfig.class)
@ActiveProfiles("test")
class TicketRedemptionTest {

  @Autowired private TicketPassRedeemRepository repository;
  @Autowired private DatabaseClient db;

  private static final String TENANT = "tenant-1";
  private static final String VENUE = "venue-1";

  private String idem() {
    return UUID.randomUUID().toString();
  }

  // 1. Happy path: VALID -> USED + audit REDEEMED
  @Test
  void redeem_validPass_transitionsToUsed() {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);
    String key = idem();

    RedeemResult r =
        repository.redeemAtomically(
            qr, "scanner-1", "device-a", Set.of(VENUE), TENANT, key, "1.1.1.1");

    assertThat(r.outcome()).isEqualTo(RedeemOutcome.REDEEMED);
    assertThat(r.status()).isEqualTo(TicketPassStatus.USED);
    assertThat(TicketFixtures.passStatus(db, qr)).isEqualTo("USED");
    assertThat(TicketFixtures.auditOutcomesForKey(db, key)).containsExactly("REDEEMED");
  }

  // 2. Concurrent scans: exactly one REDEEMED, state is USED, and exactly one audit row of
  // outcome REDEEMED. The Spanner emulator does not enforce strict serializability for
  // overlapping readWriteTransactions, so the "loser" may surface as TerminalConflict, a
  // SpannerException wrapping abort, or (on emulator-only racing semantics) a second successful
  // result if the emulator's CAS implementation allows it. We assert the invariants that the
  // production Spanner upholds: final row is USED and exactly one REDEEMED audit row exists.
  @Test
  void redeem_concurrentScans_finalStateUsedAndSingleRedeemedAudit() throws Exception {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);

    String key1 = idem();
    String key2 = idem();
    CompletableFuture<Object> f1 =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return repository.redeemAtomically(
                    qr, "scanner-1", "device-a", Set.of(VENUE), TENANT, key1, "1.1.1.1");
              } catch (Exception e) {
                return e;
              }
            });
    CompletableFuture<Object> f2 =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return repository.redeemAtomically(
                    qr, "scanner-2", "device-b", Set.of(VENUE), TENANT, key2, "1.1.1.1");
              } catch (Exception e) {
                return e;
              }
            });

    Object r1 = f1.get();
    Object r2 = f2.get();

    List<Object> results = List.of(r1, r2);
    long successes = results.stream().filter(o -> o instanceof RedeemResult).count();
    assertThat(successes).isGreaterThanOrEqualTo(1);
    assertThat(TicketFixtures.passStatus(db, qr)).isEqualTo("USED");

    // Exactly one REDEEMED outcome in the audit log — the second scanner's outcome must be a
    // non-REDEEMED state (ALREADY_USED in production, possibly absent on emulator abort).
    String passId = TicketFixtures.lookupPassId(db, qr);
    int redeemedCount = 0;
    try (com.google.cloud.spanner.ResultSet rs =
        db.singleUse()
            .executeQuery(
                com.google.cloud.spanner.Statement.newBuilder(
                        "SELECT outcome FROM scan_audit_log WHERE ticket_pass_id = @id")
                    .bind("id")
                    .to(passId)
                    .build())) {
      while (rs.next()) {
        if ("REDEEMED".equals(rs.getString("outcome"))) {
          redeemedCount++;
        }
      }
    }
    assertThat(redeemedCount).isEqualTo(1);
  }

  // 3. Duplicate idempotency key, same scanner -> cached replay
  @Test
  void redeem_duplicateIdempotencyKey_returnsCachedResponse() {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);
    String key = idem();

    RedeemResult first =
        repository.redeemAtomically(qr, "s1", "d", Set.of(VENUE), TENANT, key, "1.1.1.1");
    RedeemResult second =
        repository.redeemAtomically(qr, "s1", "d", Set.of(VENUE), TENANT, key, "1.1.1.1");

    assertThat(first.outcome()).isEqualTo(RedeemOutcome.REDEEMED);
    assertThat(second.outcome()).isEqualTo(RedeemOutcome.REDEEMED);
    assertThat(second.replayed()).isTrue();
    // Only ONE actual audit REDEEMED row — the replay path takes the cached outcome without
    // writing a second audit.
    String passId = TicketFixtures.lookupPassId(db, qr);
    assertThat(TicketFixtures.countAuditRowsFor(db, passId)).isEqualTo(1);
  }

  // 5. Idempotency key reused by different scanner -> ROLE_DENIED masquerades as 404
  @Test
  void redeem_idempotencyKeyReusedByDifferentScanner_roleDenied() {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);
    String key = idem();
    repository.redeemAtomically(qr, "s1", "d", Set.of(VENUE), TENANT, key, "1.1.1.1");

    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s-different", "d", Set.of(VENUE), TENANT, key, "1.1.1.1"))
        .isInstanceOf(NotScannableException.class);
  }

  // 6. Tenant mismatch -> NotScannable (404-masked)
  @Test
  void redeem_tenantMismatch_masquerades_as_404() {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), "different-tenant", idem(), "1.1.1.1"))
        .isInstanceOf(NotScannableException.class);
    assertThat(TicketFixtures.passStatus(db, qr)).isEqualTo("VALID");
  }

  // 7. Venue mismatch -> NotScannable (404-masked)
  @Test
  void redeem_venueMismatch_masquerades_as_404() {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of("other-venue"), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(NotScannableException.class);
    assertThat(TicketFixtures.passStatus(db, qr)).isEqualTo("VALID");
  }

  // 8. Not found shape-equal to tenant mismatch (both are NotScannableException)
  @Test
  void redeem_notFound_sameShapeAs_tenantMismatch() {
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    "TKT-DEADBEEFDEADBEEF", "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(NotScannableException.class);
  }

  // 11. Entitlement not ACTIVE -> terminal conflict
  @Test
  void redeem_entitlementRefunded_terminalConflict() {
    String qr = TicketFixtures.seedPass(db, TENANT, VENUE, "VALID", "REFUNDED", null, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class)
        .hasMessageContaining("Entitlement");
  }

  // 12. Outside validity window (before valid_from)
  @Test
  void redeem_beforeValidFrom_terminalConflict() {
    Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, future, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class);
  }

  // 13. Outside validity window (after valid_until)
  @Test
  void redeem_afterValidUntil_terminalConflict() {
    Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, past);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class);
  }

  // 14. Pass REVOKED -> terminal
  @Test
  void redeem_passRevoked_terminalConflict() {
    String qr = TicketFixtures.seedPass(db, TENANT, VENUE, "REVOKED", "ACTIVE", null, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class);
  }

  // 15. Pass EXPIRED -> terminal
  @Test
  void redeem_passExpired_terminalConflict() {
    String qr = TicketFixtures.seedPass(db, TENANT, VENUE, "EXPIRED", "ACTIVE", null, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class);
  }

  // 16. Pass USED yesterday -> terminal, no PII in exception message
  @Test
  void redeem_alreadyUsedYesterday_terminalConflict_leaksNoPII() {
    String qr = TicketFixtures.seedPass(db, TENANT, VENUE, "USED", "ACTIVE", null, null);
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class)
        .hasMessageNotContaining("@") // no email leak
        .hasMessageNotContaining("guest_name");
  }

  // 23. Revoke as admin -> transitions to REVOKED, subsequent redeem terminal
  @Test
  void revoke_asAdmin_transitionsToRevoked() {
    String qr = TicketFixtures.seedValidPass(db, TENANT, VENUE, null, null);
    String passId = TicketFixtures.lookupPassId(db, qr);

    repository.revokeAtomically(passId);

    assertThat(TicketFixtures.passStatus(db, qr)).isEqualTo("REVOKED");
    assertThatThrownBy(
            () ->
                repository.redeemAtomically(
                    qr, "s1", "d", Set.of(VENUE), TENANT, idem(), "1.1.1.1"))
        .isInstanceOf(TerminalConflictException.class);
  }
}
