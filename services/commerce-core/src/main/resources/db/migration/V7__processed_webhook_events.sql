-- Replay-protection table for external payment webhooks (Stripe today, PayPay
-- later). The unique PK on event_id gives us a belt-and-suspenders idempotency
-- guarantee in addition to the CAS-on-status logic in PaymentServiceImpl:
-- even if a webhook arrives with a repeated provider event id, we bail out
-- before touching the payment/order state machine.
CREATE TABLE processed_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_webhook_events_received_at
    ON processed_webhook_events(received_at);
