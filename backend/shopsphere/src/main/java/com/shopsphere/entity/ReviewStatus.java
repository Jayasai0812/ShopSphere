package com.shopsphere.entity;

/**
 * Moderation lifecycle of a review (TASK-018).
 *
 * PENDING  — freshly submitted or edited; visible only to its author.
 * APPROVED — admin-approved; counted in the product average and listed
 *            publicly on the product endpoints.
 * REJECTED — hidden by moderation but retained for the author's history
 *            (the one-review-per-product slot stays occupied).
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
