package com.shopsphere;

import com.jayway.jsonpath.JsonPath;
import com.shopsphere.entity.Category;
import com.shopsphere.entity.Inventory;
import com.shopsphere.entity.Order;
import com.shopsphere.entity.OrderItem;
import com.shopsphere.entity.Product;
import com.shopsphere.entity.User;
import com.shopsphere.enums.OrderStatus;
import com.shopsphere.repository.CategoryRepository;
import com.shopsphere.repository.OrderRepository;
import com.shopsphere.repository.ProductRepository;
import com.shopsphere.repository.RoleRepository;
import com.shopsphere.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-018 review acceptance tests. Every request is authenticated with a
 * real JWT from /auth/login, so the tests exercise the full filter +
 * CurrentUserProvider pipeline. Unique emails/SKUs per test guard against
 * cross-test interference on the shared in-memory schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ProductRepository productRepository;

        @Autowired
        private CategoryRepository categoryRepository;

        @Autowired
        private OrderRepository orderRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private org.springframework.transaction.PlatformTransactionManager transactionManager;

        private static final String PASSWORD = "Password1";

        /** Registers a user and returns a valid Bearer token for them. */
        private String registerAndLogin(String email) throws Exception {
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Jane\",\"email\":\"%s\",\"password\":\"%s\",\"phone\":null}"
                                                .formatted(email, PASSWORD)))
                                .andExpect(status().isCreated());

                MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                                .andExpect(status().isOk())
                                .andReturn();
                return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        }

        /** Creates a product (active, in stock) and returns its id. */
        private long createProduct(String sku) {
                User seller = userRepository.findByEmail("review-seller@shopsphere.test")
                                .orElseGet(() -> {
                                        User s = new User();
                                        s.setName("Review Seller");
                                        s.setEmail("review-seller@shopsphere.test");
                                        s.setPasswordHash("x");
                                        return userRepository.save(s);
                                });
                Category category = categoryRepository.findByNameIgnoreCase("Review Test Category")
                                .orElseGet(() -> {
                                        Category c = new Category();
                                        c.setName("Review Test Category");
                                        return categoryRepository.save(c);
                                });
                Product p = new Product();
                p.setName("Product " + sku);
                p.setSku(sku);
                p.setPrice(new BigDecimal("499.00"));
                p.setCategory(category);
                p.setSeller(seller);
                p.setActive(true);
                Inventory inv = new Inventory();
                inv.setProduct(p);
                inv.setStockQuantity(10);
                p.setInventory(inv);
                return productRepository.save(p).getId();
        }

        /**
         * Simulates a completed purchase: creates a DELIVERED order for the
         * user containing the product, bypassing the cart flow (statuses
         * beyond PENDING are not yet reachable through the API).
         */
        private void createDeliveredOrder(User user, Product product) {
                Order order = new Order();
                order.setUser(user);
                order.setStatus(OrderStatus.DELIVERED);
                order.setShippingAddressLine1("1 Test Lane");
                order.setShippingCity("Pune");
                order.setShippingState("MH");
                order.setShippingPincode("411001");
                order.setShippingCountry("India");
                order.setTotalAmount(product.getPrice());
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProduct(product);
                item.setProductName(product.getName());
                item.setUnitPrice(product.getPrice());
                item.setQuantity(1);
                item.setLineTotal(product.getPrice());
                order.getItems().add(item);
                orderRepository.save(order);
        }

        private User userByEmail(String email) {
                return userRepository.findByEmail(email).orElseThrow();
        }

        @Test
        void getWithoutTokenReturns401() throws Exception {
                // Unknown product: the public list endpoint returns 404 for a
                // nonexistent product (TASK-018 improvement), which still proves
                // the route is publicly reachable — 401 would mean auth blocked it.
                mockMvc.perform(get("/api/v1/products/999999/reviews"))
                                .andExpect(status().isNotFound());

                mockMvc.perform(post("/api/v1/products/1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":5}"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(get("/api/v1/admin/reviews"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void nonPurchaserCannotReview() throws Exception {
                String token = registerAndLogin("rev-nopurchase@example.com");
                long productId = createProduct("REV-SKU-001");

                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":5,\"comment\":\"Never bought this\"}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.detail")
                                                .value("You can only review products you have purchased"));
        }

        @Test
        void ratingValidationRejectsOutOfRangeAndFractional() throws Exception {
                String email = "rev-validation@example.com";
                String token = registerAndLogin(email);
                long productId = createProduct("REV-SKU-002");
                // A non-purchaser gets 403 before validation; give this user a
                // delivered purchase so the rating rules themselves are tested.
                createDeliveredOrder(userByEmail(email),
                                productRepository.findById(productId).orElseThrow());

                for (String bad : new String[] { "0", "6", "-1" }) {
                        mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"rating\":" + bad + "}"))
                                        .andExpect(status().isBadRequest());
                }

                // Numeric fractional rating: BigDecimal preserves the decimal
                // form so @Digits(fraction = 0) rejects it with 400 — this was
                // the bypass in the original Integer-based DTO.
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":3.5}"))
                                .andExpect(status().isBadRequest());

                // A valid whole-number rating for the SAME purchaser must still
                // work, proving the rejection above isn't a blanket block.
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":3}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.rating").value(3));

                // Boundary values as JSON numbers: 0 and 6 both rejected.
                mockMvc.perform(delete("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isNoContent());
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":0}"))
                                .andExpect(status().isBadRequest());
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":6}"))
                                .andExpect(status().isBadRequest());

                // Missing rating entirely.
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"no rating\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void unknownProductReturns404() throws Exception {
                String token = registerAndLogin("rev-404@example.com");

                mockMvc.perform(post("/api/v1/products/99999/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":5}"))
                                .andExpect(status().isNotFound());
        }

        /** Full happy path: purchase → review → moderation → public visibility. */
        @Test
        void purchaserCanReviewOnceAndOnlyApprovedAppearPublicly() throws Exception {
                String token = registerAndLogin("rev-purchaser@example.com");
                long productId = createProduct("REV-SKU-003");
                createDeliveredOrder(userByEmail("rev-purchaser@example.com"),
                                productRepository.findById(productId).orElseThrow());

                // Submit — enters moderation as PENDING, so not public yet.
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":5,\"comment\":\"Great product\"}"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.rating").value(5))
                                .andExpect(jsonPath("$.productId").value((int) productId));

                // Not public yet: no rating on the product, no public review.
                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.rating.reviewCount").value(0))
                                .andExpect(jsonPath("$.rating.averageRating").doesNotExist());

                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews"))
                                .andExpect(jsonPath("$.content.length()").value(0));

                // Second submission for the same product is rejected (one per user).
                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":4}"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.detail")
                                                .value("You have already reviewed this product"));

                // Admin approves → now public and counted.
                String adminToken = adminToken();
                MvcResult queue = mockMvc.perform(get("/api/v1/admin/reviews?status=PENDING")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andReturn();
                long reviewId = ((Number) JsonPath.read(queue.getResponse().getContentAsString(),
                                "$.content[0].id")).longValue();

                mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/approve")
                                .header("Authorization", "Bearer " + adminToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("APPROVED"));

                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(jsonPath("$.rating.reviewCount").value(1))
                                .andExpect(jsonPath("$.rating.averageRating").value(5.0));

                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews"))
                                .andExpect(jsonPath("$.content.length()").value(1))
                                .andExpect(jsonPath("$.content[0].authorName").value("Jane"))
                                .andExpect(jsonPath("$.content[0].rating").value(5));
        }

        @Test
        void userCanUpdateAndDeleteOwnReviewButNotOthers() throws Exception {
                String authorToken = registerAndLogin("rev-author@example.com");
                long productId = createProduct("REV-SKU-004");
                createDeliveredOrder(userByEmail("rev-author@example.com"),
                                productRepository.findById(productId).orElseThrow());

                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + authorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":3}"))
                                .andExpect(status().isCreated());

                // Author updates: rating changes and status resets to PENDING.
                mockMvc.perform(put("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + authorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":4,\"comment\":\"Updated opinion\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.rating").value(4))
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.comment").value("Updated opinion"));

                // Author reads their own review.
                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + authorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.rating").value(4));

                // Another user (who never reviewed) gets 404 on /me of the
                // same product — reviews are addressed per-user, no id leaks.
                String strangerToken = registerAndLogin("rev-stranger@example.com");
                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + strangerToken))
                                .andExpect(status().isNotFound());

                // Author deletes; then /me is 404 and the product rating is empty.
                mockMvc.perform(delete("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + authorToken))
                                .andExpect(status().isNoContent());

                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + authorToken))
                                .andExpect(status().isNotFound());

                mockMvc.perform(get("/api/v1/products/" + productId))
                                .andExpect(jsonPath("$.rating.reviewCount").value(0));
        }

        @Test
        void nonAdminCannotModerate() throws Exception {
                String token = registerAndLogin("rev-customer@example.com");

                mockMvc.perform(get("/api/v1/admin/reviews")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isForbidden());

                mockMvc.perform(patch("/api/v1/admin/reviews/1/approve")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isForbidden());
        }

        /** Grants ADMIN by direct persistence, then logs in normally. */
        private String adminToken() throws Exception {
                String email = "rev-admin@example.com";
                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Admin\",\"email\":\"%s\",\"password\":\"%s\",\"phone\":null}"
                                                .formatted(email, PASSWORD)))
                                .andExpect(status().isCreated());

                User admin = userRepository.findByEmail(email).orElseThrow();
                com.shopsphere.entity.Role role = roleRepository.findByRoleName("ADMIN")
                                .orElseGet(() -> {
                                        com.shopsphere.entity.Role r = new com.shopsphere.entity.Role();
                                        r.setRoleName("ADMIN");
                                        return roleRepository.save(r);
                                });
                // Attach the role through a short transactional block: the
                // @Modifying insert needs an active transaction.
                new org.springframework.transaction.support.TransactionTemplate(
                                transactionManager)
                        .executeWithoutResult(tx -> roleRepository
                                        .linkUserToRole(admin.getId(), role.getId()));

                MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                                .andExpect(status().isOk())
                                .andReturn();
                return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        }

        /**
         * TASK-018 fix A: /reviews/me must NOT fall under the public
         * catalog GET rule. Proves all four required access outcomes.
         */
        @Test
        void reviewsMeIsAuthenticatedOnly() throws Exception {
                String authorEmail = "rev-me-author@example.com";
                String authorToken = registerAndLogin(authorEmail);
                long productId = createProduct("REV-SKU-008");
                createDeliveredOrder(userByEmail(authorEmail),
                                productRepository.findById(productId).orElseThrow());

                mockMvc.perform(post("/api/v1/products/" + productId + "/reviews")
                                .header("Authorization", "Bearer " + authorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rating\":4,\"comment\":\"Mine\"}"))
                                .andExpect(status().isCreated());

                // 1. Public review list without a token → 200 (stays public).
                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content.length()").value(0)); // nothing approved yet

                // 2. /reviews/me without a token → 401, NOT 200/404.
                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews/me"))
                                .andExpect(status().isUnauthorized());

                // 3. Author's own token → 200.
                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + authorToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.rating").value(4));

                // 4. Another user's token → 404 (their own review doesn't exist;
                // existence of the author's review is not leaked).
                String strangerToken = registerAndLogin("rev-me-stranger@example.com");
                mockMvc.perform(get("/api/v1/products/" + productId + "/reviews/me")
                                .header("Authorization", "Bearer " + strangerToken))
                                .andExpect(status().isNotFound());
        }
}
