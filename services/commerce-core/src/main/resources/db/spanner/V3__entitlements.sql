CREATE TABLE entitlements (
    entitlement_id STRING(36) NOT NULL,
    order_id STRING(36) NOT NULL,
    order_item_id STRING(36) NOT NULL,
    user_id STRING(36) NOT NULL,
    product_variant_id STRING(36) NOT NULL,
    type STRING(32) NOT NULL,
    status STRING(32) NOT NULL,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (entitlement_id);

CREATE INDEX idx_entitlements_user ON entitlements(user_id);
CREATE INDEX idx_entitlements_order ON entitlements(order_id);

CREATE TABLE ticket_passes (
    ticket_pass_id STRING(36) NOT NULL,
    entitlement_id STRING(36) NOT NULL,
    qr_code_payload STRING(255) NOT NULL,
    status STRING(32) NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (ticket_pass_id);

CREATE INDEX idx_ticket_passes_entitlement ON ticket_passes(entitlement_id)
