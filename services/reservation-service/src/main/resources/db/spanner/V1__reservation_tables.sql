CREATE TABLE reservation_slots (
    slot_id STRING(36) NOT NULL,
    tenant_id STRING(36) NOT NULL,
    venue_id STRING(36) NOT NULL,
    product_id STRING(36) NOT NULL,
    slot_date STRING(10) NOT NULL,
    start_time STRING(5) NOT NULL,
    end_time STRING(5) NOT NULL,
    capacity INT64 NOT NULL,
    approved_count INT64 NOT NULL,
    waitlist_count INT64 NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (slot_id);

CREATE INDEX idx_reservation_slots_venue_date ON reservation_slots(venue_id, slot_date);

CREATE TABLE reservations (
    reservation_id STRING(36) NOT NULL,
    tenant_id STRING(36) NOT NULL,
    venue_id STRING(36) NOT NULL,
    slot_id STRING(36) NOT NULL,
    consumer_user_id STRING(36) NOT NULL,
    status STRING(32) NOT NULL,
    idempotency_key STRING(64) NOT NULL,
    guest_name STRING(255) NOT NULL,
    guest_email STRING(255) NOT NULL,
    guest_count INT64 NOT NULL,
    reject_reason STRING(1024),
    cancel_reason STRING(1024),
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (reservation_id);

CREATE UNIQUE INDEX idx_reservations_idempotency ON reservations(idempotency_key);
CREATE INDEX idx_reservations_venue_status ON reservations(venue_id, status, created_at);
CREATE INDEX idx_reservations_consumer ON reservations(consumer_user_id, created_at);
