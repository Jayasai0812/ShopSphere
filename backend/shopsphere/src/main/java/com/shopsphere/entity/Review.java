package com.shopsphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer review of a purchased product (TASK-018).
 *
 * Invariants enforced here and in ReviewService:
 * - ONE review per (user, product) — DB-level unique constraint
 * uk_reviews_user_product; the service also exists-checks to return a
 * clean 409 for the common repeated-submit case.
 * - rating is 1..5; also enforced at the DB layer by CHECK constraints in
 * V5 (H2 and MySQL both honor them) so no code path can persist 0 or 6.
 * - A review exists only for a product the user actually purchased in a
 * DELIVERED order (verified against order history in the service).
 *
 * Moderation: {@code status} is the admin-controlled lifecycle. PENDING is
 * the initial state; only APPROVED reviews are counted in the product's
 * average and returned on the public product endpoints. REJECTED hides a
 * review without deleting the author's history. Admins moderate via
 * /api/v1/admin/reviews; users can edit or delete their own review, which
 * resets it to PENDING so edits are re-moderated.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_reviews_user_product", columnNames = {
                "user_id", "product_id" }), indexes = {
                                @Index(name = "idx_reviews_product_id", columnList = "product_id"),
                                @Index(name = "idx_reviews_status", columnList = "status")
                })
@Getter
@Setter
@NoArgsConstructor
public class Review extends Auditable {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "product_id", nullable = false)
        private Product product;

        @Column(nullable = false)
        private int rating;

        @Lob
        @Column(name = "comment_text", length = 2000)
        private String comment;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private ReviewStatus status = ReviewStatus.PENDING;
}
