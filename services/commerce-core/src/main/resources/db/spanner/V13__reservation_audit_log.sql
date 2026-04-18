CREATE TABLE reservation_audit_log (
  log_id STRING(36) NOT NULL,
  reservation_id STRING(36) NOT NULL,
  action STRING(32) NOT NULL,
  actor_user_id STRING(36),
  reason STRING(1024),
  created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (log_id);

CREATE INDEX idx_audit_log_reservation ON reservation_audit_log(reservation_id, created_at);
