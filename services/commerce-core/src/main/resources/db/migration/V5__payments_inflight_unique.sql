-- Enforce at most one in-flight payment per order. CREATED and PROCESSING are
-- the "in-flight" states; SUCCEEDED and FAILED are terminal and may coexist
-- with a retry. A partial unique index lets concurrent createPaymentIntent
-- calls with different idempotency keys race safely: one wins the insert, the
-- other gets a constraint violation that the service translates to a
-- ConflictException.
CREATE UNIQUE INDEX uniq_payments_inflight_order
    ON payments(order_id)
    WHERE status IN ('CREATED', 'PROCESSING');
