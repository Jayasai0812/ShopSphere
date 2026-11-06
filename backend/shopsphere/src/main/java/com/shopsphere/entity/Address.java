package com.shopsphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A saved delivery address belonging to one user.
 *
 * {@code defaultAddress} is enforced in the service layer, not here:
 * at most one address per user is the default, and when a new default
 * is chosen the previous one is un-set in the same transaction.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Recipient name for delivery, e.g. the user's own name or a family
     * member's when shipping as a gift.
     */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Contact phone number for the delivery courier. */
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "line1", nullable = false, length = 255)
    private String line1;

    /** Optional second line: apartment, floor, landmark, etc. */
    @Column(name = "line2", length = 255)
    private String line2;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    @Column(nullable = false, length = 12)
    private String pincode;

    @Column(nullable = false, length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 10)
    private AddressType addressType = AddressType.HOME;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    /**
     * Guard against legacy rows or manual inserts: the schema has no
     * DB-level default for the new columns, so persistence-time nulls
     * become the enum's default value.
     */
    @PrePersist
    @PreUpdate
    void normalize() {
        if (addressType == null) {
            addressType = AddressType.HOME;
        }
    }
}
