-- Adds product_variant_id to inventory_holds so the orders/checkout flow can
-- validate that an inventory hold matches the requested product variant
-- (introduced alongside the orders.validate-slot-belongs-to-variant fix).
ALTER TABLE inventory_holds ADD COLUMN product_variant_id STRING(36);
