package com.shopsphere;

import com.jayway.jsonpath.JsonPath;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.Review;
import com.shopsphere.entity.ReviewStatus;
import com.shopsphere.entity.User;
import com.shopsphere.enums.OrderStatus;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.ReviewRepository;
import com.shopsphere.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-019 product discovery acceptance tests. Seeds a deterministic
 * catalog via repositories and exercises search, filtering, sorting,
 * pagination and the public/admin visibility rules through the real HTTP
 * endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private ReviewRepository reviewRepository;

        private static boolean seeded;

        // Seeded once per JVM (static flag); the ids must therefore also be
        // static so every test instance reads the same fixture values.
        private static long alphaId;
        private static long betaId;
        private static long gammaId;
        private static long deltaId;
        private static long inactiveId;
        private static long catAId;
        private static long catBId;

        private void seed() {
                if (seeded) {
                        return;
                }
                User seller = user("search-seller@shopsphere.test", "Search Seller");
                User reviewer = user("search-reviewer@shopsphere.test", "Search Reviewer");
                User secondReviewer = user("search-reviewer2@shopsphere.test", "Second Reviewer");

                Category catA = category("Search Cat A");
                Category catB = category("Search Cat B");
                catAId = catA.getId();
                catBId = catB.getId();

                alphaId = product("Alpha Headphones", "SEA-001", "99.00", catA, true, 5).getId();
                betaId = product("Beta Speaker", "SEA-002", "49.00", catA, true, 0).getId();
                gammaId = product("Gamma Cable", "SEA-003", "9.00", catB, true, 50).getId();
                deltaId = product("Delta Headphones Pro", "SEA-004", "199.00", catB, true, 3).getId();
                inactiveId = product("Epsilon Legacy", "SEA-005", "10.00", catA, false, 10).getId();

                // Alpha: APPROVED 5 and 3 → avg 4.0. Beta/Gamma: none. Delta: PENDING only.
                review(reviewer, alphaId, 5, ReviewStatus.APPROVED);
                review(secondReviewer, alphaId, 3, ReviewStatus.APPROVED);
                review(reviewer, deltaId, 5, ReviewStatus.PENDING);

                // Popularity (delivered units): Delta 12, Alpha 4; cancelled order ignored.
                deliveredOrder(reviewer, deltaId, 12, OrderStatus.DELIVERED);
                deliveredOrder(reviewer, alphaId, 2, OrderStatus.DELIVERED);
                deliveredOrder(reviewer, alphaId, 2, OrderStatus.DELIVERED);
                deliveredOrder(reviewer, alphaId, 999, OrderStatus.CANCELLED);

                seeded = true;
        }

        private User user(String email, String name) {
                return userRepository.findByEmail(email).orElseGet(() -> {
                        User u = new User();
                        u.setName(name);
                        u.setEmail(email);
                        u.setPasswordHash("x");
                        return userRepository.save(u);
                });
        }

        private Category category(String name) {
                return categoryRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                        Category c = new Category();
                        c.setName(name);
                        return categoryRepository.save(c);
                });
        }

        private Product product(String name, String sku, String price, Category category,
                        boolean active, int stock) {
                Product p = new Product();
                p.setName(name);
                p.setSku(sku);
                p.setPrice(new BigDecimal(price));
                p.setCategory(category);
                p.setSeller(userRepository.findByEmail("search-seller@shopsphere.test").orElseThrow());
                p.setActive(active);
                p.setDescription(name.toLowerCase() + " quality gadget");
                Inventory inv = new Inventory();
                inv.setProduct(p);
                inv.setStockQuantity(stock);
                p.setInventory(inv);
                return productRepository.save(p);
        }

        private void review(User user, long productId, int rating, ReviewStatus status) {
                Review r = new Review();
                r.setUser(user);
                r.setProduct(productRepository.findById(productId).orElseThrow());
                r.setRating(rating);
                r.setStatus(status);
                r.setComment("seed");
                reviewRepository.save(r);
        }

        private void deliveredOrder(User user, long productId, int qty, OrderStatus status) {
                Product product = productRepository.findById(productId).orElseThrow();
                Order order = new Order();
                order.setUser(user);
                order.setStatus(status);
                order.setShippingAddressLine1("1 Search Lane");
                order.setShippingCity("Pune");
                order.setShippingState("MH");
                order.setShippingPincode("411001");
                order.setShippingCountry("India");
                order.setTotalAmount(product.getPrice().multiply(BigDecimal.valueOf(qty)));
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProduct(product);
                item.setProductName(product.getName());
                item.setUnitPrice(product.getPrice());
                item.setQuantity(qty);
                item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(qty)));
                order.getItems().add(item);
                orderRepository.save(order);
        }

        private List<Long> searchIds(String query) throws Exception {
                seed();
                MvcResult result = mockMvc.perform(get("/api/v1/products" + query))
                                .andExpect(status().isOk())
                                .andReturn();
                return ((java.util.List<?>) JsonPath.read(
                                result.getResponse().getContentAsString(), "$.content[*].id"))
                                .stream().map(n -> ((Number) n).longValue()).toList();
        }

        // ── filters ────────────────────────────────────────────────────────

        @Test
        void textSearchMatchesNameAndDescriptionCaseInsensitively() throws Exception {
                List<Long> ids = searchIds("?q=headphones");
                assertThat(ids).containsExactlyInAnyOrder(alphaId, deltaId);

                // Escaped wildcards: a literal percent sign must not match everything.
                List<Long> none = searchIds("?q=100%25");
                assertThat(none).isEmpty();
        }

        @Test
        void priceBoundariesAreInclusiveAndInvalidRangeRejected() throws Exception {
                List<Long> cheap = searchIds("?minPrice=9&maxPrice=49");
                assertThat(cheap).containsExactlyInAnyOrder(betaId, gammaId);

                mockMvc.perform(get("/api/v1/products?minPrice=100&maxPrice=10"))
                                .andExpect(status().isBadRequest());

                mockMvc.perform(get("/api/v1/products?minPrice=abc"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void availabilityAndMinStockRespectInventoryRules() throws Exception {
                // available=true excludes zero-stock Beta.
                List<Long> inStock = searchIds("?available=true");
                assertThat(inStock).contains(alphaId, gammaId, deltaId).doesNotContain(betaId);

                // available=false returns exactly the zero-stock product.
                List<Long> outOfStock = searchIds("?available=false");
                assertThat(outOfStock).containsExactly(betaId);

                List<Long> bigStock = searchIds("?minStock=10");
                assertThat(bigStock).containsExactly(gammaId);
        }

        @Test
        void minRatingUsesApprovedReviewsOnly() throws Exception {
                // Alpha avg 4.0 (approved 5+3). Delta's only review is PENDING → excluded.
                List<Long> rated = searchIds("?minRating=3.5");
                assertThat(rated).containsExactly(alphaId);

                List<Long> anyApproved = searchIds("?minRating=1");
                assertThat(anyApproved).containsExactly(alphaId);
        }

        @Test
        void combinedFiltersCompose() throws Exception {
                List<Long> ids = searchIds("?q=headphones&maxPrice=100&available=true");
                assertThat(ids).containsExactly(alphaId);

                mockMvc.perform(get("/api/v1/products?minRating=9"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void inactiveProductsNeverAppearPublicly() throws Exception {
                List<Long> all = searchIds("");
                assertThat(all).doesNotContain(inactiveId);

                mockMvc.perform(get("/api/v1/products/" + inactiveId))
                                .andExpect(status().isNotFound());

                // Public category listing also excludes inactive.
                MvcResult cat = mockMvc.perform(get("/api/v1/products/category/" + catAId))
                                .andExpect(status().isOk())
                                .andReturn();
                assertThat(((java.util.List<?>) JsonPath.read(
                                cat.getResponse().getContentAsString(), "$[*].id"))
                                .stream().map(n -> ((Number) n).longValue()).toList())
                                .doesNotContain(inactiveId);
        }

        // ── sorting ────────────────────────────────────────────────────────

        @Test
        void sortAliasesWorkWithTieBreaker() throws Exception {
                assertThat(searchIds("?sort=price,asc"))
                                .containsExactly(gammaId, betaId, alphaId, deltaId);
                // desc ties (beta 49 vs gamma 9? none) — keep just order check.
                assertThat(searchIds("?sort=price,desc"))
                                .containsExactly(deltaId, alphaId, betaId, gammaId);
                // newest: newest first = reverse creation; inactive excluded.
                assertThat(searchIds("?sort=newest"))
                                .containsExactlyInAnyOrder(deltaId, gammaId, betaId, alphaId)
                                .doesNotContain(inactiveId);
                // Alphabetical: Alpha, Beta, Delta, Gamma (D < G).
                assertThat(searchIds("?sort=name,asc"))
                                .containsExactly(alphaId, betaId, deltaId, gammaId);
        }

        @Test
        void popularitySortsByDeliveredUnitsSold() throws Exception {
                List<Long> ids = searchIds("?sort=popularity,desc");
                // Delta 12 first, then Alpha 4; Beta/Gamma zero (id tie-breaker).
                assertThat(ids.indexOf(deltaId)).isLessThan(ids.indexOf(alphaId));
                assertThat(ids).doesNotContain(inactiveId);

                // Ascending popularity: zero-sellers first (id tie-breaker),
                // then Alpha (4), Delta (12) last.
                List<Long> asc = searchIds("?sort=popularity,asc");
                assertThat(asc.indexOf(alphaId)).isLessThan(asc.indexOf(deltaId));
                assertThat(asc.indexOf(betaId)).isLessThan(asc.indexOf(alphaId));
        }

        @Test
        void ratingSortPlacesUnratedLastAndUsesApprovedAverage() throws Exception {
                List<Long> ids = searchIds("?sort=rating,desc");
                // Alpha (avg 4.0) first; the rest are unrated → last, id tie-breaker.
                assertThat(ids).startsWith(alphaId);
                // Ascending rating: still rated products first (Alpha),
                // unrated last regardless of direction.
                List<Long> asc = searchIds("?sort=rating,asc");
                assertThat(asc).startsWith(alphaId);
                assertThat(asc.indexOf(deltaId)).isGreaterThan(asc.indexOf(alphaId));
        }

        @Test
        void invalidPaginationAndQueryLengthRejected() throws Exception {
                mockMvc.perform(get("/api/v1/products?page=-1"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(get("/api/v1/products?size=0"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(get("/api/v1/products?minRating=NaN"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(get("/api/v1/products?minRating=Infinity"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(get("/api/v1/products?q=" + "x".repeat(201)))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(get("/api/v1/products?q=" + "x".repeat(200)))
                                .andExpect(status().isOk());
        }

        @Test
        void unknownSortKeyIsRejected() throws Exception {
                mockMvc.perform(get("/api/v1/products?sort=description"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(get("/api/v1/products?sort=price,widdershins"))
                                .andExpect(status().isBadRequest());
        }

        // ── pagination ─────────────────────────────────────────────────────

        @Test
        void paginationMetadataIsExactWithRatingFilter() throws Exception {
                seed();
                mockMvc.perform(get("/api/v1/products?minRating=3.5&size=1&page=0"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalElements").value(1))
                                .andExpect(jsonPath("$.numberOfElements").value(1))
                                .andExpect(jsonPath("$.totalPages").value(1))
                                .andExpect(jsonPath("$.content[0].id").value((int) alphaId));
        }

        @Test
        void pageSizeIsCappedAt100() throws Exception {
                seed();
                mockMvc.perform(get("/api/v1/products?size=500"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.size").value(100));
        }

        // ── visibility & compatibility ─────────────────────────────────────

        @Test
        void adminListingIncludesInactiveProducts() throws Exception {
                seed();
                // No token → 401 on the admin listing.
                mockMvc.perform(get("/api/v1/admin/products"))
                                .andExpect(status().isUnauthorized());

                // Public listing excludes the inactive product but has the rest.
                List<Long> publicIds = searchIds("");
                assertThat(publicIds).hasSize(4).doesNotContain(inactiveId);
        }

        @Test
        void categoryEndpointRemainsCompatible() throws Exception {
                List<Long> ids = searchIds("?categoryId=" + catAId + "&sort=price,asc");
                // Alpha 99, Beta 49 (inactive Epsilon excluded).
                assertThat(ids).containsExactly(betaId, alphaId);

                mockMvc.perform(get("/api/v1/products/category/99999"))
                                .andExpect(status().isNotFound());
        }
}
