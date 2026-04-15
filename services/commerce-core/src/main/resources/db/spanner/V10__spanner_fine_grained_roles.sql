-- Fine-grained access control roles for ticketing workloads.
-- Grants the ticketing-service GSA (bound via Workload Identity) the
-- minimum privileges needed for redemption. Bootstrap runs as a
-- database admin, so CREATE ROLE + GRANT succeed here even though the
-- ticketing-service runtime will run under roles/spanner.databaseUser
-- plus roles/spanner.fineGrainedAccessUser with a condition selecting
-- the ticketing_service database role.
--
-- CLAUDE.md pitfall: never edit this file after the first deploy.
-- Any later change must land in a new V<n> file.

CREATE ROLE ticketing_service;
GRANT INSERT ON TABLE scan_audit_log TO ROLE ticketing_service;
GRANT INSERT, SELECT ON TABLE ticket_redeem_idempotency TO ROLE ticketing_service;
-- UPDATE needed because revokeSession uses Mutation.newInsertOrUpdateBuilder (same
-- (user_id, session_id) can be re-revoked; admin idempotency relies on upsert semantics).
GRANT INSERT, SELECT, UPDATE ON TABLE revoked_sessions TO ROLE ticketing_service;
GRANT SELECT, UPDATE ON TABLE ticket_passes TO ROLE ticketing_service;
GRANT SELECT ON TABLE entitlements TO ROLE ticketing_service;

CREATE ROLE ticketing_backfill;
GRANT SELECT, UPDATE ON TABLE ticket_passes TO ROLE ticketing_backfill;
GRANT SELECT ON TABLE entitlements TO ROLE ticketing_backfill;
