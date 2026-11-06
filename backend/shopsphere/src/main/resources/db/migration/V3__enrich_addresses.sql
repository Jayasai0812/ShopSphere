-- V3 — TASK-013: address management — enrich saved delivery addresses
--
-- Extends the addresses table with recipient identity fields required by
-- the address CRUD feature (POST/PUT /api/v1/users/me/addresses):
--   * full_name    — delivery recipient (may differ from the account name)
--   * phone        — contact number for the courier
--   * line2        — optional second address line
--   * address_type — HOME / WORK / OTHER (stored as enum name string)
--
-- Existing rows are backfilled from each owning user's account name, then
-- the new columns are made NOT NULL. On a fresh database the backfills are
-- no-ops. address_type defaults to 'HOME'.
--
-- Note: Flyway is not yet configured in this project — execute this script
-- manually against shopsphere_db for now (e.g.
--   mysql -u root -p shopsphere_db < V3__enrich_addresses.sql
-- ). Adding Flyway is a separate improvement.

USE shopsphere_db;

ALTER TABLE addresses
    ADD COLUMN full_name    VARCHAR(100) NULL AFTER user_id,
    ADD COLUMN phone        VARCHAR(20)  NULL AFTER full_name,
    ADD COLUMN line2        VARCHAR(255) NULL AFTER line1,
    ADD COLUMN address_type VARCHAR(10)  NOT NULL DEFAULT 'HOME' AFTER country;

-- Backfill recipient identity from the owning user's account.
UPDATE addresses a
JOIN users u ON u.id = a.user_id
SET
    a.full_name = COALESCE(u.name, 'UNKNOWN'),
    a.phone     = COALESCE(u.phone, 'UNKNOWN');

ALTER TABLE addresses
    MODIFY COLUMN full_name VARCHAR(100) NOT NULL,
    MODIFY COLUMN phone     VARCHAR(20)  NOT NULL;
