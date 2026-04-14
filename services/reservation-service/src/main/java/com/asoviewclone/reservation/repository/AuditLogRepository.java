package com.asoviewclone.reservation.repository;

import com.asoviewclone.reservation.model.AuditLog;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.Statement;
import com.google.cloud.spanner.Value;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

  private final DatabaseClient databaseClient;

  public AuditLogRepository(DatabaseClient databaseClient) {
    this.databaseClient = databaseClient;
  }

  /**
   * Buffer an audit log mutation within an existing transaction. Call this inside
   * readWriteTransaction.run() to write the audit row atomically with state transitions.
   */
  public static Mutation createMutation(
      String reservationId, String action, String actorUserId, String reason) {
    Mutation.WriteBuilder builder =
        Mutation.newInsertBuilder("reservation_audit_log")
            .set("log_id")
            .to(UUID.randomUUID().toString())
            .set("reservation_id")
            .to(reservationId)
            .set("action")
            .to(action)
            .set("created_at")
            .to(Value.COMMIT_TIMESTAMP);

    if (actorUserId != null) {
      builder.set("actor_user_id").to(actorUserId);
    } else {
      builder.set("actor_user_id").to((String) null);
    }

    if (reason != null) {
      builder.set("reason").to(reason);
    } else {
      builder.set("reason").to((String) null);
    }

    return builder.build();
  }

  public List<AuditLog> findByReservationId(String reservationId) {
    Statement stmt =
        Statement.newBuilder(
                "SELECT * FROM reservation_audit_log"
                    + " WHERE reservation_id = @reservationId"
                    + " ORDER BY created_at")
            .bind("reservationId")
            .to(reservationId)
            .build();
    List<AuditLog> results = new ArrayList<>();
    try (ResultSet rs = databaseClient.singleUse().executeQuery(stmt)) {
      while (rs.next()) {
        results.add(fromResultSet(rs));
      }
    }
    return results;
  }

  private AuditLog fromResultSet(ResultSet rs) {
    return new AuditLog(
        rs.getString("log_id"),
        rs.getString("reservation_id"),
        rs.getString("action"),
        rs.isNull("actor_user_id") ? null : rs.getString("actor_user_id"),
        rs.isNull("reason") ? null : rs.getString("reason"),
        rs.getTimestamp("created_at").toSqlTimestamp().toInstant());
  }
}
