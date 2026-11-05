-- Migration V2 — TASK-012.1 (checkout shipping snapshot + inventory optimistic locking)
-- Applies the checkout changes to databases created from the TASK-006 baseline schema.
-- Fresh installs can simply run ../shopsphere_schema.sql, which already includes these columns.
--
-- 1. orders: add the shipping-address snapshot columns (NOT NULL — backfilled
--    from each user's default address so historical rows satisfy the constraint).
-- 2. inventory: add the @Version column used for optimistic locking at checkout.

USE shopsphere_db;

-- ── 1. Shipping-address snapshot on orders ──────────────────────────────────

-- Backfill from each user's default address (falls back to any address).
UPDATE orders o
JOIN users u ON u.id = o.user_id
LEFT JOIN addresses a
       ON a.user_id = u.id
      AND a.is_default = 1
SET
    o.shipping_address_line1 = COALESCE(a.line1, 'UNKNOWN'),
    o.shipping_city          = COALESCE(a.city, 'UNKNOWN'),
    o.shipping_state         = COALESCE(a.state, 'UNKNOWN'),
    o.shipping_pincode       = COALESCE(a.pincode, 'UNKNOWN'),
    o.shipping_country       = COALESCE(a.country, 'UNKNOWN');

ALTER TABLE orders
    ADD COLUMN shipping_address_line1 VARCHAR(255) NULL AFTER status,
    ADD COLUMN shipping_city          VARCHAR(100) NULL AFTER shipping_address_line1,
    ADD COLUMN shipping_state         VARCHAR(100) NULL AFTER shipping_city,
    ADD COLUMN shipping_pincode       VARCHAR(12)  NULL AFTER shipping_state,
    ADD COLUMN shipping_country       VARCHAR(100) NULL AFTER shipping_pincode;

-- Backfill again now that the columns exist (two-step keeps the ALTER reversible).
UPDATE orders o
JOIN users u ON u.id = o.user_id
LEFT JOIN addresses a
       ON a.user_id = u.id
      AND a.is_default = 1
SET
    o.shipping_address_line1 = COALESCE(a.line1, 'UNKNOWN'),
    o.shipping_city          = COALESCE(a.city, 'UNKNOWN'),
    o.shipping_state         = COALESCE(a.state, 'UNKNOWN'),
    o.shipping_pincode       = COALESCE(a.pincode, 'UNKNOWN'),
    o.shipping_country       = COALESCE(a.country, 'UNKNOWN');

ALTER TABLE orders
    MODIFY COLUMN shipping_address_line1 VARCHAR(255) NOT NULL,
    MODIFY COLUMN shipping_city          VARCHAR(100) NOT NULL,
    MODIFY COLUMN shipping_state         VARCHAR(100) NOT NULL,
    MODIFY COLUMN shipping_pincode       VARCHAR(12)  NOT NULL,
    MODIFY COLUMN shipping_country       VARCHAR(100) NOT NULL;

-- ── 2. Optimistic-lock version on inventory ─────────────────────────────────

ALTER TABLE inventory
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER stock_quantity;
