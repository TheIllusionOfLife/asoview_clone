CREATE TABLE inventory_slots (
    slot_id STRING(36) NOT NULL,
    product_variant_id STRING(36) NOT NULL,
    slot_date STRING(10) NOT NULL,
    start_time STRING(5),
    end_time STRING(5),
    total_capacity INT64 NOT NULL,
    reserved_count INT64 NOT NULL DEFAULT (0),
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (slot_id);

CREATE INDEX idx_slots_variant_date ON inventory_slots(product_variant_id, slot_date);

CREATE TABLE inventory_holds (
    hold_id STRING(36) NOT NULL,
    slot_id STRING(36) NOT NULL,
    product_variant_id STRING(36) NOT NULL,
    user_id STRING(36) NOT NULL,
    quantity INT64 NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (hold_id);

CREATE INDEX idx_holds_slot ON inventory_holds(slot_id);
CREATE INDEX idx_holds_expires ON inventory_holds(expires_at);
