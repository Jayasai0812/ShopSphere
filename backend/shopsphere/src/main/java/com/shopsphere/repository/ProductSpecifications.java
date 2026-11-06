package com.shopsphere.repository;

import com.shopsphere.entity.Product;
import com.shopsphere.enums.OrderStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

/**
 * Composable query specifications for product discovery (TASK-019).
 *
 * Every method returns a null-safe partial predicate; the service composes
 * them with AND. Public queries always start with {@link #isActive()}.
 *
 * Rating and popularity are implemented as CORRELATED SUBQUERIES in the
 * outer product query (approved with the reviewer's Option B adjustment):
 * this keeps the outer result at exactly one row per product, so pagination
 * counts stay exact, while letting the database filter/sort on aggregated
 * values (approved-review average, delivered units sold).
 *
 * Subquery semantics per the review:
 * - rating: SELECT product_id FROM reviews WHERE status='APPROVED'
 * GROUP BY product_id HAVING AVG(rating) >= :minRating
 * → products without approved reviews never qualify.
 * - popularity (sort only): SUM of OrderItem.quantity over DELIVERED
 * orders; products with no sales sort last (NULLS LAST).
 */
public final class ProductSpecifications {

        private ProductSpecifications() {
        }

        /** Public visibility rule: soft-deleted/deactivated products never match. */
        public static Specification<Product> isActive() {
                return (root, query, cb) -> cb.isTrue(root.get("active"));
        }

        /**
         * Case-insensitive AND-of-tokens match: the query is split on
         * whitespace and EVERY token must match the name OR the description
         * (each token independently). % and _ are escaped.
         */
        public static Specification<Product> textSearch(String q) {
                return (root, query, cb) -> {
                        char escapeChar = '\\';
                        Predicate[] tokens = java.util.Arrays
                                        .stream(q.toLowerCase().trim().split("\\s+"))
                                        .filter(token -> !token.isEmpty())
                                        .map(token -> {
                                                String pattern = "%" + escapeLike(token) + "%";
                                                Predicate inName = cb.like(cb.lower(root.get("name")),
                                                                pattern, escapeChar);
                                                Predicate inDescription = cb.like(
                                                                cb.lower(root.get("description")),
                                                                pattern, escapeChar);
                                                return cb.or(inName, inDescription);
                                        })
                                        .toArray(Predicate[]::new);
                        return cb.and(tokens);
                };
        }

        public static Specification<Product> inCategory(Long categoryId) {
                return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
        }

        public static Specification<Product> priceAtLeast(BigDecimal min) {
                return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
        }

        public static Specification<Product> priceAtMost(BigDecimal max) {
                return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
        }

        /**
         * Availability filter. Inventory is a @OneToOne(mappedBy="product")
         * association — the join is over its FK, one row per product, so no
         * row multiplication. Available=true requires stock > 0, so an inner
         * join is used (a missing inventory row means zero stock → excluded).
         * Available=false means OUT OF STOCK only for products that HAVE an
         * inventory row with quantity 0; products with no inventory row are
         * treated as unavailable/zero stock but are kept in the result set
         * via the LEFT JOIN + isNull check.
         */
        public static Specification<Product> withAvailability(Boolean available) {
                return (root, query, cb) -> {
                        var inventory = root.join("inventory", JoinType.LEFT);
                        var stock = cb.coalesce(inventory.get("stockQuantity").as(Integer.class), 0);
                        if (Boolean.TRUE.equals(available)) {
                                return cb.greaterThan(stock, 0);
                        }
                        return cb.equal(stock, 0);
                };
        }

        /** Stock quantity >= minStock (products without inventory are excluded
         *  — they have zero stock, so they can never satisfy a positive minimum). */
        public static Specification<Product> withMinStock(Integer minStock) {
                return (root, query, cb) -> {
                        var inventory = root.join("inventory", JoinType.LEFT);
                        var stock = cb.coalesce(inventory.get("stockQuantity").as(Integer.class), 0);
                        return cb.greaterThanOrEqualTo(stock, minStock);
                };
        }

        /**
         * Option B rating floor as a correlated subquery:
         * p.id IN (SELECT r.product_id FROM reviews r
         * WHERE r.status = 'APPROVED' AND r.product_id = p.id
         * GROUP BY r.product_id HAVING AVG(r.rating) >= :minRating)
         */
        public static Specification<Product> withMinRating(Double minRating) {
                return (root, query, cb) -> {
                        var sub = query.subquery(Long.class);
                        var review = sub.from(com.shopsphere.entity.Review.class);
                        sub.select(review.get("product").get("id"))
                                        .where(cb.equal(review.get("product"), root),
                                                        cb.equal(review.get("status"),
                                                                        com.shopsphere.entity.ReviewStatus.APPROVED))
                                        .groupBy(review.get("product").get("id"))
                                        .having(cb.greaterThanOrEqualTo(
                                                        cb.avg(review.get("rating")).as(Double.class),
                                                        minRating));
                        return cb.in(root.get("id")).value(sub);
                };
        }

        /**
         * Units-sold expression for SORTING only: SUM(OrderItem.quantity)
         * over the product's order items whose order status is DELIVERED.
         * Correlated scalar subquery via cb.<Long>exists-free scalar select;
         * the subquery root (orderItem) is created FROM the subquery so all
         * criteria nodes stay in the same query tree. Products with no
         * sales get 0 (COALESCE); the id tie-breaker keeps order stable.
         */
        public static jakarta.persistence.criteria.Expression<Long> unitsSold(
                        jakarta.persistence.criteria.CriteriaQuery<?> query,
                        jakarta.persistence.criteria.CriteriaBuilder cb,
                        jakarta.persistence.criteria.Root<Product> root) {
                var sub = query.subquery(Long.class);
                var orderItem = sub.from(com.shopsphere.entity.OrderItem.class);
                sub.select(cb.sum(orderItem.get("quantity")))
                                .where(cb.equal(
                                                orderItem.get("product").get("id"),
                                                root.get("id")),
                                                cb.equal(orderItem.get("order").get("status"),
                                                                OrderStatus.DELIVERED));
                return cb.coalesce(sub, 0L);
        }

        /**
         * Approved-average-rating expression for SORTING only — same
         * aggregation rules as the displayed rating and the minRating
         * filter (only APPROVED reviews). Unrated products sort LAST via
         * NULLS LAST on a NULL-producing correlated subquery.
         */
        public static jakarta.persistence.criteria.Expression<Double> approvedAverageRating(
                        jakarta.persistence.criteria.CriteriaQuery<?> query,
                        jakarta.persistence.criteria.CriteriaBuilder cb,
                        jakarta.persistence.criteria.Root<Product> root) {
                var sub = query.subquery(Double.class);
                var review = sub.from(com.shopsphere.entity.Review.class);
                sub.select(cb.avg(review.get("rating")))
                                .where(cb.equal(
                                                review.get("product").get("id"),
                                                root.get("id")),
                                                cb.equal(review.get("status"),
                                                                com.shopsphere.entity.ReviewStatus.APPROVED))
                                .groupBy(review.get("product").get("id"));
                // A product with no approved reviews yields NULL in the
                // correlated evaluation → sorts last via nullsLast ordering.
                return sub;
        }

        /**
         * Deterministic pagination: appends the unique id as the final sort
         * key after whatever Pageable sort was requested. Applied only when
         * the caller did NOT request an expression sort (rating/popularity),
         * which already include their own id tie-breaker.
         */
        public static org.springframework.data.domain.Sort idTieBreaker(
                        org.springframework.data.domain.Sort existing) {
                for (org.springframework.data.domain.Sort.Order order : existing) {
                        if (order.getProperty().equals("id")) {
                                return existing;
                        }
                }
                return existing.and(org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.asc("id")));
        }

        /** Escapes LIKE wildcards so users can search for literal % and _. */
        static String escapeLike(String input) {
                StringBuilder sb = new StringBuilder(input.length());
                for (char c : input.toCharArray()) {
                        if (c == '%' || c == '\\' || c == '_') {
                                sb.append('\\');
                        }
                        sb.append(c);
                }
                return sb.toString();
        }

        public static Specification<Product> and(List<Specification<Product>> specs) {
                Specification<Product> combined = null;
                for (Specification<Product> spec : specs) {
                        if (spec == null) {
                                continue;
                        }
                        combined = combined == null ? spec : combined.and(spec);
                }
                return combined;
        }
}
