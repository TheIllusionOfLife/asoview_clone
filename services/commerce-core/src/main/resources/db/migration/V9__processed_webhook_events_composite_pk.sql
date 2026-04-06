-- Make replay protection per (provider, event_id), not per event_id alone.
-- Stripe and PayPay can mint identical event id strings; once both gateways
-- are live a single global PK would silently swallow a real PayPay event
-- whose id collides with a previously-seen Stripe event. Same-provider
-- replays still hit the composite key and are correctly de-duped.
--
-- Existing rows are stamped 'STRIPE' (the only provider in production today).
-- The PK swap is mechanical because no FKs reference processed_webhook_events.

ALTER TABLE processed_webhook_events
    DROP CONSTRAINT processed_webhook_events_pkey;

ALTER TABLE processed_webhook_events
    ADD PRIMARY KEY (provider, event_id);
