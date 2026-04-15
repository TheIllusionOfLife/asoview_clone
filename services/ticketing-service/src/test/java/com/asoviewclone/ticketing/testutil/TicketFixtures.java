package com.asoviewclone.ticketing.testutil;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Helpers to seed entitlements and ticket_passes rows for repository/service tests. */
public final class TicketFixtures {

  private TicketFixtures() {}

  public static String randomQrPayload() {
    return "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
  }

  public static String seedValidPass(
      DatabaseClient db, String tenantId, String venueId, Instant validFrom, Instant validUntil) {
    return seedPass(db, tenantId, venueId, "VALID", "ACTIVE", validFrom, validUntil);
  }

  public static String seedPass(
      DatabaseClient db,
      String tenantId,
      String venueId,
      String passStatus,
      String entitlementStatus,
      Instant validFrom,
      Instant validUntil) {
    String passId = UUID.randomUUID().toString();
    String entitlementId = UUID.randomUUID().toString();
    String qr = randomQrPayload();

    Mutation.WriteBuilder eb =
        Mutation.newInsertBuilder("entitlements")
            .set("entitlement_id")
            .to(entitlementId)
            .set("order_id")
            .to(UUID.randomUUID().toString())
            .set("order_item_id")
            .to(UUID.randomUUID().toString())
            .set("user_id")
            .to(UUID.randomUUID().toString())
            .set("product_variant_id")
            .to(UUID.randomUUID().toString())
            .set("type")
            .to("TICKET")
            .set("status")
            .to(entitlementStatus)
            .set("created_at")
            .to(Value.COMMIT_TIMESTAMP);
    if (validFrom != null) {
      eb.set("valid_from")
          .to(com.google.cloud.Timestamp.ofTimeSecondsAndNanos(validFrom.getEpochSecond(), 0));
    }
    if (validUntil != null) {
      eb.set("valid_until")
          .to(com.google.cloud.Timestamp.ofTimeSecondsAndNanos(validUntil.getEpochSecond(), 0));
    }

    Mutation.WriteBuilder pb =
        Mutation.newInsertBuilder("ticket_passes")
            .set("ticket_pass_id")
            .to(passId)
            .set("entitlement_id")
            .to(entitlementId)
            .set("qr_code_payload")
            .to(qr)
            .set("status")
            .to(passStatus)
            .set("tenant_id")
            .to(tenantId)
            .set("venue_id")
            .to(venueId)
            .set("created_at")
            .to(Value.COMMIT_TIMESTAMP);

    db.write(List.of(eb.build(), pb.build()));
    return qr;
  }

  public static int countAuditRowsFor(DatabaseClient db, String passId) {
    Statement stmt =
        Statement.newBuilder("SELECT COUNT(*) AS c FROM scan_audit_log WHERE ticket_pass_id = @id")
            .bind("id")
            .to(passId)
            .build();
    try (ResultSet rs = db.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return (int) rs.getLong("c");
      }
    }
    return 0;
  }

  public static List<String> auditOutcomesForKey(DatabaseClient db, String idempotencyKey) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT outcome FROM scan_audit_log WHERE idempotency_key = @k"
                    + " ORDER BY scanned_at")
            .bind("k")
            .to(idempotencyKey)
            .build();
    List<String> outcomes = new ArrayList<>();
    try (ResultSet rs = db.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        outcomes.add(rs.getString("outcome"));
      }
    }
    return outcomes;
  }

  public static String passStatus(DatabaseClient db, String qrPayload) {
    Statement stmt =
        Statement.newBuilder("SELECT status FROM ticket_passes WHERE qr_code_payload = @q")
            .bind("q")
            .to(qrPayload)
            .build();
    try (ResultSet rs = db.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getString("status");
      }
    }
    return null;
  }

  public static String lookupPassId(DatabaseClient db, String qrPayload) {
    Statement stmt =
        Statement.newBuilder("SELECT ticket_pass_id FROM ticket_passes WHERE qr_code_payload = @q")
            .bind("q")
            .to(qrPayload)
            .build();
    try (ResultSet rs = db.singleUse().executeQuery(stmt)) {
      if (rs.next()) {
        return rs.getString("ticket_pass_id");
      }
    }
    return null;
  }
}
