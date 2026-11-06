package com.shopsphere.entity;

/**
 * Kind of saved delivery address. Stored in the database as the enum
 * name string ("HOME", "WORK", "OTHER"); Jackson deserializes incoming
 * JSON values case-insensitively and rejects anything else with 400.
 */
public enum AddressType {
    HOME,
    WORK,
    OTHER
}
