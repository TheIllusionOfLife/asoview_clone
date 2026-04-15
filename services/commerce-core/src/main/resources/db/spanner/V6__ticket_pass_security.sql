ALTER TABLE ticket_passes ADD COLUMN venue_id STRING(36);
ALTER TABLE ticket_passes ADD COLUMN tenant_id STRING(36);
-- Allow server-side commit timestamp writes to used_at so the redeem path can CAS with
-- `Value.COMMIT_TIMESTAMP` instead of trusting a client clock.
ALTER TABLE ticket_passes ALTER COLUMN used_at SET OPTIONS (allow_commit_timestamp=true);
CREATE INDEX idx_ticket_passes_qr ON ticket_passes(qr_code_payload);
