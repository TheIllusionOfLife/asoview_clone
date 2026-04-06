-- Enforce at most one payment row per provider_payment_id so the webhook
-- lookup (findByProviderPaymentId) returns a single row and replay detection
-- is anchored on a unique DB constraint. Partial so existing NULL rows (from
-- older payments created before Stripe integration) do not collide.
CREATE UNIQUE INDEX uniq_payments_provider_payment_id
    ON payments(provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
