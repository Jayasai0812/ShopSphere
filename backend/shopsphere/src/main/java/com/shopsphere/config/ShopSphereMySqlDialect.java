package com.shopsphere.config;

import org.hibernate.dialect.MySQLDialect;
import org.hibernate.type.SqlTypes;

/**
 * Keeps audit columns on MySQL TIMESTAMP(6). Hibernate's default MySQL dialect
 * emits DATETIME for {@link java.time.Instant}, which would fail schema validation
 * against shopsphere_schema.sql.
 */
public class ShopSphereMySqlDialect extends MySQLDialect {

    @Override
    protected String columnType(int sqlTypeCode) {
        if (sqlTypeCode == SqlTypes.TIMESTAMP) {
            return "timestamp($p)";
        }
        return super.columnType(sqlTypeCode);
    }
}
