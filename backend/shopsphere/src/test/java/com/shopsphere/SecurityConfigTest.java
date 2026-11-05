package com.shopsphere;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance tests for TASK-013.1 (security foundation), mirroring the review
 * criteria:
 * 1. health endpoint is public
 * 2. protected endpoints return 401 without a token
 * 3. invalid/expired tokens are rejected with 401
 * 4. a valid JWT establishes an authenticated SecurityContext
 * 5. CUSTOMER cannot reach ADMIN-only endpoints (403)
 * 6. ADMIN can reach ADMIN-only endpoints
 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.transaction.annotation.Transactional
class SecurityConfigTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private com.shopsphere.security.JwtService jwtService;
        @Autowired
        private com.shopsphere.repository.UserRepository userRepository;
        @Autowired
        private com.shopsphere.repository.RoleRepository roleRepository;

        private String bearerFor(com.shopsphere.entity.User user) {
                return "Bearer " + jwtService.generateToken(
                    user.getEmail(), user.getId(),
                    user.getRoles().stream().map(r -> r.getRoleName()).toList());
        }

    protected com.shopsphere.entity.User persistUser(String email, String roleName) {
                com.shopsphere.entity.User user = new com.shopsphere.entity.User();
                user.setName("Security Test User");
                user.setEmail(email);
                user.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
                com.shopsphere.entity.Role role = roleRepository.findByRoleName(roleName)
                    .orElseGet(() -> {
                        com.shopsphere.entity.Role r = new com.shopsphere.entity.Role();
                        r.setRoleName(roleName);
                        return roleRepository.save(r);
                    });
                user.getRoles().add(role);
                return userRepository.save(user);
        }

        @AfterEach
        void clearContext() {
                SecurityContextHolder.clearContext();
        }

        // 1. Public endpoints
        @Test
        void healthIsPubliclyAccessible() throws Exception {
                mockMvc.perform(get("/api/v1/health"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        void catalogBrowsingIsPublic() throws Exception {
                mockMvc.perform(get("/api/v1/products"))
                                .andExpect(status().isOk());
                mockMvc.perform(get("/api/v1/categories"))
                                .andExpect(status().isOk());
        }

        // 2. 401 without token
        @Test
        void protectedEndpointReturns401WithoutToken() throws Exception {
                mockMvc.perform(get("/api/v1/orders"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/api/v1/cart"))
                                .andExpect(status().isUnauthorized());
                mockMvc.perform(get("/api/v1/orders"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void catalogWriteRequiresAuthentication() throws Exception {
                mockMvc.perform(post("/api/v1/categories")
                                .contentType("application/json")
                                .content("{\"name\":\"Sneaky Category\"}"))
                                .andExpect(status().isUnauthorized());
        }

        // 3. Invalid / garbage tokens
        @Test
        void invalidTokenReturns401() throws Exception {
                mockMvc.perform(get("/api/v1/orders")
                                .header("Authorization", "Bearer not-a-real-token"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void expiredTokenReturns401() throws Exception {
                String expired = io.jsonwebtoken.Jwts.builder()
                                .subject("expired@shopsphere.test")
                                .claim("userId", 1L)
                                .claim("roles", java.util.List.of("CUSTOMER"))
                                .issuedAt(new java.util.Date(System.currentTimeMillis() - 7_200_000))
                                .expiration(new java.util.Date(System.currentTimeMillis() - 3_600_000))
                                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                                io.jsonwebtoken.io.Decoders.BASE64.decode(
                                                                "ZGV2LW9ubHktc2VjcmV0LWtleS1mb3Itc2hvcHNwaGVyZS10ZXN0cy0yNTYtYml0cy1taW5pbXVtLWtleS1tYXRlcmlhbA==")))
                                .compact();

                mockMvc.perform(get("/api/v1/orders")
                                .header("Authorization", "Bearer " + expired))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void tokenForUnknownUserReturns401() throws Exception {
                // Valid signature but no matching account — the per-request
                // UserDetailsService lookup must reject it.
                mockMvc.perform(get("/api/v1/orders")
                                .header("Authorization",
                                                "Bearer " + jwtService.generateToken(
                                                                "ghost@shopsphere.test", 999999L,
                                                                java.util.List.of("CUSTOMER"))))
                                .andExpect(status().isUnauthorized());
        }

        // 4. Valid token → authenticated request succeeds
        @Test
        void validJwtGrantsAccessToProtectedEndpoint() throws Exception {
                var customer = persistUser("valid-jwt@shopsphere.test", "CUSTOMER");

                mockMvc.perform(get("/api/v1/orders")
                                .header("Authorization", bearerFor(customer)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").exists());
        }

        // 5/6. Role-based access to ADMIN-only endpoints
        @Test
        void customerCannotCreateCategory() throws Exception {
                var customer = persistUser("customer-only@shopsphere.test", "CUSTOMER");

                mockMvc.perform(post("/api/v1/categories")
                                .header("Authorization", bearerFor(customer))
                                .contentType("application/json")
                                .content("{\"name\":\"Role Test Category\"}"))
                                .andExpect(status().isForbidden());
        }

        @Test
        void customerCannotDeleteProduct() throws Exception {
                var customer = persistUser("customer-del@shopsphere.test", "CUSTOMER");

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/products/1")
                                .header("Authorization", bearerFor(customer)))
                                .andExpect(status().isForbidden());
        }

        @Test
        void adminCanCreateCategory() throws Exception {
                var admin = persistUser("admin-role@shopsphere.test", "ADMIN");

                mockMvc.perform(post("/api/v1/categories")
                                .header("Authorization", bearerFor(admin))
                                .contentType("application/json")
                                .content("{\"name\":\"Admin Role Test Category "
                                                + System.currentTimeMillis() + "\"}"))
                                .andExpect(status().isCreated());
        }

        @Test
        void adminCanDeleteProduct() throws Exception {
                var admin = persistUser("admin-del@shopsphere.test", "ADMIN");

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/v1/products/1")
                                .header("Authorization", bearerFor(admin)))
                                .andExpect(result -> assertThat(result.getResponse().getStatus())
                                                .isIn(204, 404)); // 204 if the row exists, 404 otherwise
        }
}
