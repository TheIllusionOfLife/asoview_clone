CREATE TABLE ticket_redeem_idempotency (
    idempotency_key STRING(64) NOT NULL,
    scanner_user_id STRING(64) NOT NULL,
    ticket_pass_id STRING(64),
    outcome STRING(32) NOT NULL,
    response_body_hash STRING(64) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (idempotency_key),
  ROW DELETION POLICY (OLDER_THAN(created_at, INTERVAL 1 DAY))
