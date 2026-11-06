-- V5 — TASK-018: product reviews and ratings
--
-- Creates the reviews table:
--   user_id, product_id  — one review per (user, product), enforced by
--                          uk_reviews_user_product at the DB level.
--   rating               — 1..5, enforced by CHECK constraints.
--   comment              — optional free text (max 2000 chars).
--   status               — PENDING / APPROVED / REJECTED moderation
--                          lifecycle; only APPROVED reviews count toward
--                          the product's public average rating.
--   created_at/updated_at — same audit columns as other entities.
--
-- Note on @Lob vs TEXT: the entity maps comment with @Lob, which Hibernate
-- generates as CLOB/longtext on MySQL — consistent with the TEXT column
-- created here for MySQL (TEXT is MySQL's small CLOB). H2 (tests) also maps
-- both to CLOB-compatible types. Still verify with
-- spring.jpa.hibernate.ddl-auto=validate against a real MySQL schema during
-- deployment rehearsal; the H2 test run alone does not prove MySQL
-- compatibility.
--
-- Note: still NOT wired to Flyway (no flyway dependency configured).
-- Execute manually against shopsphere_db:
--   mysql -u root -p shopsphere_db < V5__create_reviews.sql
--
USE shopsphere_db;

CREATE TABLE reviews (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    rating       INT          NOT NULL,
    comment      TEXT         NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP(6) NOT NULL,
    updated_at   TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_reviews_rating_min CHECK (rating >= 1),
    CONSTRAINT ck_reviews_rating_max CHECK (rating <= 5)
);

CREATE INDEX idx_reviews_product_id ON reviews (product_id);
CREATE INDEX idx_reviews_status ON reviews (status);
