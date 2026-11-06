package com.shopsphere.dto;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and validates raw request parameters into a
 * {@link ProductSearchCriteria} (TASK-019).
 *
 * Validation rules (all failures → 400):
 * - minPrice/maxPrice must parse as non-negative BigDecimal; min > max is
 * rejected.
 * - minRating must be between 1 and 5.
 * - minStock must be non-negative.
 * - sort values must be one of the public aliases name/price/newest/
 * rating/popularity, optionally with "asc"/"desc"; unknown keys are
 * rejected — raw property names never reach the persistence layer.
 * - page size is capped at 100.
 */
public final class ProductSearchCriteriaParser {

        public static final int MAX_PAGE_SIZE = 100;

        private ProductSearchCriteriaParser() {
        }

        public static ProductSearchCriteria parse(
                        String q, Long categoryId,
                        String minPrice, String maxPrice,
                        String minRating, String available, String minStock,
                        List<String> sortParams, Integer requestedSize) {

                BigDecimal min = parsePrice(minPrice, "minPrice");
                BigDecimal max = parsePrice(maxPrice, "maxPrice");
                if (min != null && max != null && min.compareTo(max) > 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "minPrice must not be greater than maxPrice");
                }

                Double rating = null;
                if (minRating != null && !minRating.isBlank()) {
                        try {
                                rating = Double.parseDouble(minRating.trim());
                        } catch (NumberFormatException e) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "minRating must be a number");
                        }
                        if (rating < 1 || rating > 5) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "minRating must be between 1 and 5");
                        }
                }

                Integer stock = null;
                if (minStock != null && !minStock.isBlank()) {
                        try {
                                stock = Integer.parseInt(minStock.trim());
                        } catch (NumberFormatException e) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "minStock must be a non-negative integer");
                        }
                        if (stock < 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "minStock must be non-negative");
                        }
                }

                Boolean availableFlag = null;
                if (available != null && !available.isBlank()) {
                        if ("true".equalsIgnoreCase(available.trim())) {
                                availableFlag = true;
                        } else if ("false".equalsIgnoreCase(available.trim())) {
                                availableFlag = false;
                        } else {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "available must be true or false");
                        }
                }

                List<ProductSearchCriteria.SortSpec> sorts = new ArrayList<>();
                if (sortParams != null) {
                        for (String raw : sortParams) {
                                if (raw == null || raw.isBlank()) {
                                        continue;
                                }
                                String[] parts = raw.trim().split(",", 2);
                                ProductSearchCriteria.SortKey key = ProductSearchCriteria.SortKey.from(parts[0]);
                                if (key == null) {
                                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                        "Unknown sort key: " + parts[0]
                                                                        + ". Allowed: name, price, newest, rating, popularity");
                                }
                                boolean desc = false;
                                if (parts.length == 2) {
                                        if (parts[1].trim().equalsIgnoreCase("desc")) {
                                                desc = true;
                                        } else if (!parts[1].trim().equalsIgnoreCase("asc")) {
                                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                                "Sort direction must be asc or desc: " + raw);
                                        }
                                }
                                sorts.add(new ProductSearchCriteria.SortSpec(key, desc));
                        }
                }

                return new ProductSearchCriteria(
                                blankToNull(q), categoryId, min, max, rating,
                                availableFlag, stock, sorts);
        }

        private static BigDecimal parsePrice(String value, String field) {
                if (value == null || value.isBlank()) {
                        return null;
                }
                try {
                        BigDecimal price = new BigDecimal(value.trim());
                        if (price.signum() < 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                field + " must be non-negative");
                        }
                        return price;
                } catch (NumberFormatException e) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        field + " must be a valid amount");
                }
        }

        private static String blankToNull(String value) {
                return value == null || value.isBlank() ? null : value.trim();
        }
}
