package com.shopsphere.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validated search parameters for product discovery (TASK-019).
 *
 * Built by {@code ProductSearchCriteriaParser} from raw request params;
 * holds only normalized, already-validated values. Sorting is expressed as
 * public ALIASES (name/price/newest/rating/popularity) with a direction —
 * raw client property names are never accepted.
 */
public record ProductSearchCriteria(
                String q,
                Long categoryId,
                BigDecimal minPrice,
                BigDecimal maxPrice,
                Double minRating,
                Boolean available,
                Integer minStock,
                List<SortSpec> sorts) {

        /** A validated sort: public alias + direction. */
        public record SortSpec(SortKey key, boolean descending) {
        }

        public enum SortKey {
                NAME("name"), PRICE("price"), NEWEST("newest"), RATING("rating"),
                POPULARITY("popularity");

                private final String label;

                SortKey(String label) {
                        this.label = label;
                }

                public String label() {
                        return label;
                }

                public static SortKey from(String value) {
                        for (SortKey key : values()) {
                                if (key.label.equalsIgnoreCase(value)) {
                                        return key;
                                }
                        }
                        return null;
                }
        }

        public boolean hasFilters() {
                return q != null || categoryId != null || minPrice != null || maxPrice != null
                                || minRating != null || available != null || minStock != null;
        }
}
