package com.shopsphere.dto;

import com.shopsphere.entity.ReviewStatus;

import java.time.Instant;

/**
 * Public view of a review as returned by the product reviews endpoints.
 *
 * Author identity is intentionally reduced to the display name — no email,
 * no user id — so the endpoint cannot be used to enumerate customer
 * accounts. {@code status} is only meaningful on the ADMIN moderation
 * endpoints and on the author's own view; the public list exposes it so
 * the frontend can filter, but only APPROVED reviews are served there.
 */
public record ReviewResponseDTO(
                Long id,
                Long productId,
                String authorName,
                int rating,
                String comment,
                ReviewStatus status,
                Instant createdAt,
                Instant updatedAt) {
}
