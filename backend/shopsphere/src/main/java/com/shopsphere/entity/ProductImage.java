package com.shopsphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata for one product image (product image management phase).
 *
 * The BINARY DATA IS NEVER STORED IN THE DATABASE — only the public URL of
 * the file on the local storage volume ({@code app.storage.dir}). The file
 * itself is written by ProductImageService.
 *
 * {@code sortOrder} is a dense 0-based position controlled exclusively by
 * the reorder endpoint; {@code isPrimary} flags the image shown as the
 * product's main picture. The composite unique constraint guarantees at
 * most one primary image per product even under concurrent updates.
 */
@Entity
@Table(name = "product_images", indexes = @Index(name = "idx_product_images_product_id", columnList = "product_id"), uniqueConstraints = @UniqueConstraint(name = "uq_product_images_product_primary", columnNames = {"product_id", "is_primary"}))
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary")
    private Boolean primaryImage;

    public boolean isPrimaryImage() {
        return Boolean.TRUE.equals(primaryImage);
    }
}
