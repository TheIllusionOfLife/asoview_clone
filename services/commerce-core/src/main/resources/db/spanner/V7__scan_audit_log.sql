CREATE TABLE scan_audit_log (
    scan_id STRING(36) NOT NULL,
    tenant_id STRING(36) NOT NULL,
    ticket_pass_id STRING(64),
    scanner_user_id STRING(64) NOT NULL,
    scanner_device_id STRING(64),
    venue_id STRING(36),
    outcome STRING(32) NOT NULL,
    source_ip STRING(64),
    idempotency_key STRING(64),
    scanned_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (scan_id);

CREATE INDEX idx_scan_audit_pass ON scan_audit_log (ticket_pass_id, scanned_at DESC);
CREATE INDEX idx_scan_audit_scanner ON scan_audit_log (tenant_id, scanner_user_id, scanned_at DESC);
CREATE INDEX idx_scan_audit_outcome ON scan_audit_log (tenant_id, outcome, scanned_at DESC);
