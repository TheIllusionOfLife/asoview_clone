-- Persist the gateway's client_secret on the payments row so the idempotent
-- replay path in PaymentServiceImpl.createPaymentIntent can return the same
-- secret to the browser without re-fetching it from the provider. Nullable
-- because older rows from pre-Stripe payments and gateways that don't expose
-- a secret (StubPaymentGateway in non-test profiles before this column
-- existed) land NULL. The frontend always expects the value from the create
-- call, never reads it back later, so older rows are not affected.
ALTER TABLE payments
    ADD COLUMN client_secret VARCHAR(255);
