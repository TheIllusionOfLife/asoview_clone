CREATE TABLE point_balances (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    balance BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE point_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    delta BIGINT NOT NULL,
    reason VARCHAR(64) NOT NULL,
    order_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX point_ledger_user_idx ON point_ledger(user_id, created_at DESC);

-- Partial unique index: a (reason, order_id) pair can occur at most once when order_id is set.
-- This is the idempotency guard for earn/burn/refund tied to a specific order.
CREATE UNIQUE INDEX point_ledger_reason_order_uq
    ON point_ledger(reason, order_id)
    WHERE order_id IS NOT NULL;
