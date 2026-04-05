CREATE TABLE orders (
    order_id STRING(36) NOT NULL,
    user_id STRING(36) NOT NULL,
    status STRING(32) NOT NULL,
    total_amount STRING(20) NOT NULL,
    currency STRING(3) NOT NULL,
    idempotency_key STRING(64) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (order_id);

CREATE UNIQUE INDEX idx_orders_idempotency ON orders(idempotency_key);
CREATE INDEX idx_orders_user ON orders(user_id);

CREATE TABLE order_items (
    order_item_id STRING(36) NOT NULL,
    order_id STRING(36) NOT NULL,
    product_variant_id STRING(36) NOT NULL,
    slot_id STRING(36) NOT NULL,
    quantity INT64 NOT NULL,
    unit_price STRING(20) NOT NULL,
    hold_id STRING(36),
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true)
) PRIMARY KEY (order_item_id)
