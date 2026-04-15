CREATE TABLE revoked_sessions (
    user_id STRING(64) NOT NULL,
    session_id STRING(64) NOT NULL,
    revoked_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (user_id, session_id),
  ROW DELETION POLICY (OLDER_THAN(revoked_at, INTERVAL 1 DAY))
