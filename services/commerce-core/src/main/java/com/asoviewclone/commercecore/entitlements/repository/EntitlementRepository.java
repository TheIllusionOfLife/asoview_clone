package com.asoviewclone.commercecore.entitlements.repository;

import com.asoviewclone.commercecore.entitlements.model.Entitlement;
import com.asoviewclone.commercecore.entitlements.model.EntitlementStatus;
import com.asoviewclone.commercecore.entitlements.model.EntitlementType;
import com.asoviewclone.commercecore.entitlements.model.TicketPass;
import com.asoviewclone.commercecore.entitlements.model.TicketPassStatus;
import com.asoviewclone.commercecore.entitlements.model.TicketPassView;
import com.asoviewclone.common.time.ClockProvider;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class EntitlementRepository {

  private final DatabaseClient databaseClient;
  private final ClockProvider clockProvider;

  public EntitlementRepository(DatabaseClient databaseClient, ClockProvider clockProvider) {
    this.databaseClient = databaseClient;
    this.clockProvider = clockProvider;
  }

  public Entitlement save(Entitlement entitlement) {
    Instant now = clockProvider.now();
    String id =
        entitlement.entitlementId() != null
            ? entitlement.entitlementId()
            : UUID.randomUUID().toString();
    Timestamp ts = Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0);

    Mutation.WriteBuilder builder =
        Mutation.newInsertBuilder("entitlements")
            .set("entitlement_id")
            .to(id)
            .set("order_id")
            .to(entitlement.orderId())
            .set("order_item_id")
            .to(entitlement.orderItemId())
            .set("user_id")
            .to(entitlement.userId())
            .set("product_variant_id")
            .to(entitlement.productVariantId())
            .set("type")
            .to(entitlement.type().name())
            .set("status")
            .to(entitlement.status().name())
            .set("created_at")
            .to(ts);

    if (entitlement.validFrom() != null) {
      builder
          .set("valid_from")
          .to(Timestamp.ofTimeSecondsAndNanos(entitlement.validFrom().getEpochSecond(), 0));
    }
    if (entitlement.validUntil() != null) {
      builder
          .set("valid_until")
          .to(Timestamp.ofTimeSecondsAndNanos(entitlement.validUntil().getEpochSecond(), 0));
    }

    databaseClient.write(List.of(builder.build()));
    return new Entitlement(
        id,
        entitlement.orderId(),
        entitlement.orderItemId(),
        entitlement.userId(),
        entitlement.productVariantId(),
        entitlement.type(),
        entitlement.status(),
        entitlement.validFrom(),
        entitlement.validUntil(),
        now);
  }

  public TicketPass saveTicketPass(TicketPass pass) {
    Instant now = clockProvider.now();
    String id = pass.ticketPassId() != null ? pass.ticketPassId() : UUID.randomUUID().toString();
    databaseClient.write(
        List.of(
            Mutation.newInsertBuilder("ticket_passes")
                .set("ticket_pass_id")
                .to(id)
                .set("entitlement_id")
                .to(pass.entitlementId())
                .set("qr_code_payload")
                .to(pass.qrCodePayload())
                .set("status")
                .to(pass.status().name())
                .set("created_at")
                .to(Timestamp.ofTimeSecondsAndNanos(now.getEpochSecond(), 0))
                .build()));
    return new TicketPass(id, pass.entitlementId(), pass.qrCodePayload(), pass.status(), null, now);
  }

  public List<Entitlement> findByUserId(String userId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT entitlement_id, order_id, order_item_id, user_id,"
                    + " product_variant_id, type, status, valid_from, valid_until, created_at"
                    + " FROM entitlements WHERE user_id = @uid ORDER BY created_at DESC")
            .bind("uid")
            .to(userId)
            .build();
    List<Entitlement> result = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        result.add(mapEntitlement(rs));
      }
    }
    return result;
  }

  public List<TicketPass> findTicketPassesByUserId(String userId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT tp.ticket_pass_id, tp.entitlement_id, tp.qr_code_payload,"
                    + " tp.status, tp.used_at, tp.created_at"
                    + " FROM ticket_passes tp"
                    + " JOIN entitlements e ON tp.entitlement_id = e.entitlement_id"
                    + " WHERE e.user_id = @uid ORDER BY tp.created_at DESC")
            .bind("uid")
            .to(userId)
            .build();
    List<TicketPass> result = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        result.add(mapTicketPass(rs));
      }
    }
    return result;
  }

  /**
   * Returns ticket passes joined with their parent entitlement so the caller has the order id and
   * validity window in a single query (no N+1). When {@code orderIdOrNull} is non-null the result
   * is filtered to that order — used by the frontend's /tickets/[orderId] page.
   */
  public List<TicketPassView> findTicketPassViewsByUserId(String userId, String orderIdOrNull) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT tp.ticket_pass_id, tp.entitlement_id, tp.qr_code_payload, tp.status,"
                + " tp.created_at, e.order_id, e.valid_from, e.valid_until"
                + " FROM ticket_passes tp"
                + " JOIN entitlements e ON tp.entitlement_id = e.entitlement_id"
                + " WHERE e.user_id = @uid");
    if (orderIdOrNull != null) {
      sql.append(" AND e.order_id = @oid");
    }
    sql.append(" ORDER BY tp.created_at DESC");
    Statement.Builder builder = Statement.newBuilder(sql.toString()).bind("uid").to(userId);
    if (orderIdOrNull != null) {
      builder.bind("oid").to(orderIdOrNull);
    }
    List<TicketPassView> result = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(builder.build())) {
      while (rs.next()) {
        result.add(
            new TicketPassView(
                rs.getString("ticket_pass_id"),
                rs.getString("entitlement_id"),
                rs.getString("order_id"),
                rs.getString("qr_code_payload"),
                TicketPassStatus.valueOf(rs.getString("status")),
                rs.isNull("valid_from")
                    ? null
                    : rs.getTimestamp("valid_from").toSqlTimestamp().toInstant(),
                rs.isNull("valid_until")
                    ? null
                    : rs.getTimestamp("valid_until").toSqlTimestamp().toInstant(),
                rs.getTimestamp("created_at").toSqlTimestamp().toInstant()));
      }
    }
    return result;
  }

  public List<TicketPass> findTicketPassesByEntitlementId(String entitlementId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT ticket_pass_id, entitlement_id, qr_code_payload,"
                    + " status, used_at, created_at"
                    + " FROM ticket_passes WHERE entitlement_id = @eid")
            .bind("eid")
            .to(entitlementId)
            .build();
    List<TicketPass> result = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        result.add(mapTicketPass(rs));
      }
    }
    return result;
  }

  public List<Entitlement> findByOrderId(String orderId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT entitlement_id, order_id, order_item_id, user_id,"
                    + " product_variant_id, type, status, valid_from, valid_until, created_at"
                    + " FROM entitlements WHERE order_id = @oid")
            .bind("oid")
            .to(orderId)
            .build();
    List<Entitlement> result = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        result.add(mapEntitlement(rs));
      }
    }
    return result;
  }

  private Entitlement mapEntitlement(ResultSet rs) {
    return new Entitlement(
        rs.getString("entitlement_id"),
        rs.getString("order_id"),
        rs.getString("order_item_id"),
        rs.getString("user_id"),
        rs.getString("product_variant_id"),
        EntitlementType.valueOf(rs.getString("type")),
        EntitlementStatus.valueOf(rs.getString("status")),
        rs.isNull("valid_from") ? null : rs.getTimestamp("valid_from").toSqlTimestamp().toInstant(),
        rs.isNull("valid_until")
            ? null
            : rs.getTimestamp("valid_until").toSqlTimestamp().toInstant(),
        rs.getTimestamp("created_at").toSqlTimestamp().toInstant());
  }

  private TicketPass mapTicketPass(ResultSet rs) {
    return new TicketPass(
        rs.getString("ticket_pass_id"),
        rs.getString("entitlement_id"),
        rs.getString("qr_code_payload"),
        TicketPassStatus.valueOf(rs.getString("status")),
        rs.isNull("used_at") ? null : rs.getTimestamp("used_at").toSqlTimestamp().toInstant(),
        rs.getTimestamp("created_at").toSqlTimestamp().toInstant());
  }
}
