-- V4 — TASK-016 hardening: database-level one-default-per-user guarantee
--
-- The service-layer switch-unset logic runs inside a transaction, but two
-- concurrent requests can both read the old state before either commits and
-- each try to set its own candidate as the user's default. This migration
-- makes the invariant enforceable BY THE DATABASE:
--
--   is_default_key = IF(is_default = 1, user_id, NULL)
--
-- with a UNIQUE index over (is_default_key). MySQL (and InnoDB) treats the
-- NULLs of non-default rows as distinct for unique-index purposes, so any
-- number of non-default rows coexist, but two rows with is_default = 1 for
-- the same user produce identical keys and the second INSERT/UPDATE fails
-- with a duplicate-key error. The service maps that to HTTP 409 CONFLICT.
--
-- HOW TO CONSUME IN JPA (needed only if the app reads/writes the column):
-- Hibernate does not manage generated columns; if the entity gains a
-- mapping for is_default_key it MUST be
--   @Column(name = "is_default_key", insertable = false, updatable = false)
-- so Hibernate never writes it. Keeping the column unmapped in the entity
-- is also fine — the database maintains it automatically.
--
-- Note: still NOT wired to Flyway (no flyway dependency configured).
-- Execute manually against shopsphere_db:
--   mysql -u root -p shopsphere_db < V4__addresses_default_unique_guard.sql
-- Test on a copy of a database that already CONTAINS address rows. If any
-- user currently has TWO default rows (legacy data), the unique index
-- creation below will fail — first deduplicate with:
--   -- keep the newest default per user, clear the others:
--   UPDATE addresses a
--   JOIN (
--       SELECT id FROM (
--           SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id
--                                         ORDER BY created_at DESC, id DESC) AS rn
--           FROM addresses WHERE is_default = 1
--       ) ranked WHERE rn > 1
--   ) dup ON dup.id = a.id
--   SET a.is_default = 0;
--
USE shopsphere_db;

ALTER TABLE addresses
    ADD COLUMN is_default_key INT
        GENERATED ALWAYS AS (IF(is_default = 1, user_id, NULL)) STORED,
    ADD UNIQUE INDEX uq_addresses_one_default_per_user (is_default_key);
