-- V2 — TASK-012.1: checkout shipping-address snapshot + inventory optimistic locking
--
-- Adds the columns required by:
--   * Order entity      → shipping_address_line1/city/state/pincode/country
--   * Inventory entity  → version (@Version, optimistic locking at checkout)
--
-- Existing orders are backfilled from each user's default address before the
-- snapshot columns are made NOT NULL. On a fresh development database with no
-- orders the backfills are no-ops.
--
-- Note: Flyway is not yet configured in this project — execute this script
-- manually against shopsphere_db for now (e.g.
--   mysql -u root -p shopsphere_db < V2__add_order_shipping_snapshot_and_inventory_version.sql
-- ). Adding Flyway is a separate improvement.

USE shopsphere_db;

-- ── 1. Shipping-address snapshot on orders ────────────────────────────────

ALTER TABLE orders
    ADD COLUMN shipping_address_line1 VARCHAR(255) NULL AFTER status,
    ADD COLUMN shipping_city          VARCHAR(100) NULL AFTER shipping_address_line1,
    ADD COLUMN shipping_state         VARCHAR(100) NULL AFTER shipping_city,
    ADD COLUMN shipping_pincode       VARCHAR(12)  NULL AFTER shipping_state,
    ADD COLUMN shipping_country       VARCHAR(100) NULL AFTER shipping_pincode;

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
    MODIFY COLUMN shipping_address_line1 VARCHAR(255) NOT NULL,
    MODIFY COLUMN shipping_city          VARCHAR(100) NOT NULL,
    MODIFY COLUMN shipping_state         VARCHAR(100) NOT NULL,
    MODIFY COLUMN shipping_pincode       VARCHAR(12)  NOT NULL,
    MODIFY COLUMN shipping_country       VARCHAR(100) NOT NULL;

-- ── 2. Optimistic-lock version on inventory ───────────────────────────────

ALTER TABLE inventory
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER stock_quantity;
