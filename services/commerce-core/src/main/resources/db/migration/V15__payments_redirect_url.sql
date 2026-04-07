-- Provider-hosted redirect / checkout URL (PayPay QR / hosted page). Null for
-- in-page gateways such as Stripe Elements that confirm via clientSecret.
ALTER TABLE payments ADD COLUMN redirect_url TEXT;
