-- V6 — image management phase: product image schema.
--
-- Bring product_images in line with the ProductImage entity:
--   image_url   — public URL of the stored binary (already present).
--   sort_order  — dense 0-based position controlled by the reorder endpoint.
--   is_primary  — NULLABLE flag; NULL means "not primary". Exactly one row
--                 per product may be TRUE, enforced by the composite unique
--                 constraint (product_id, is_primary) — in MySQL a UNIQUE
--                 index treats NULLs as distinct, so any number of NULL
--                 (non-primary) rows coexist with the single TRUE row.
--
-- The migration is idempotent-safe to run once against an existing database
-- with product image data: it backfills deterministic sort orders and picks
-- exactly one primary per product (the lowest id — the first uploaded).
--
-- Note: NOT wired to Flyway (no flyway dependency configured). Execute
-- manually against shopsphere_db:
--   mysql -u root -p shopsphere_db < V6__product_image_management.sql
--
USE shopsphere_db;

-- 1. Missing columns.
ALTER TABLE product_images
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
    ADD COLUMN is_primary BIT NULL;

-- 2. Backfill deterministic sort_order: dense 0-based positions per
--    product, ordered by upload order (id).
SET @rn := 0;
SET @prev := NULL;
UPDATE product_images pi_
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY product_id ORDER BY id) - 1 AS pos
    FROM product_images
) ranked ON ranked.id = pi_.id
SET pi_.sort_order = ranked.pos;

-- 3. Exactly one primary per product: the first-uploaded image (lowest id).
UPDATE product_images pi_
JOIN (
    SELECT product_id, MIN(id) AS first_id
    FROM product_images
    GROUP BY product_id
) firsts ON firsts.product_id = pi_.product_id AND firsts.first_id = pi_.id
SET pi_.is_primary = b'1';

-- 4. All other images are explicitly non-primary (NULL).
UPDATE product_images
SET is_primary = NULL
WHERE is_primary IS NULL;

-- 5. Enforce at most one primary per product at the DB level.
ALTER TABLE product_images
    ADD CONSTRAINT uq_product_images_product_primary
    UNIQUE (product_id, is_primary);

CREATE INDEX idx_product_images_product_id ON product_images (product_id);
