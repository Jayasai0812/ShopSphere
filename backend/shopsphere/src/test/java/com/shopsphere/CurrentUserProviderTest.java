package com.shopsphere;

import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.security.JwtService;
import com.shopsphere.security.SecurityContextCurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-style tests for {@link SecurityContextCurrentUserProvider} and the
 * {@link JwtService} claims it relies on. The provider is fed an
 * authentication token built exactly the way {@code JwtAuthenticationFilter}
 * builds it: UserDetails principal, validated JWT as credentials.
 */
@SpringBootTest
class CurrentUserProviderTest {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private SecurityContextCurrentUserProvider provider;
    @Autowired
    private UserRepository userRepository;

    private final List<String> createdEmails = new java.util.ArrayList<>();

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        createdEmails.forEach(email ->
                userRepository.findByEmail(email).ifPresent(userRepository::delete));
    }

    private void authenticate(Long userId, String email) {
        String token = jwtService.generateToken(email, userId, List.of("CUSTOMER"));
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername(email).password("n/a").roles("CUSTOMER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token,
                        principal.getAuthorities()));
    }

    @Test
    void returnsUserIdFromValidJwtInSecurityContext() {
        User user = persistUser("provider-user@shopsphere.test");
        authenticate(user.getId(), user.getEmail());

        assertThat(provider.getCurrentUserId()).isEqualTo(user.getId());
    }

    @Test
    void throwsUnauthorizedWhenSecurityContextIsEmpty() {
        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No authenticated user");
    }

    @Test
    void throwsUnauthorizedWhenPrincipalIsNotUserDetails() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", "token", List.of()));

        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No authenticated user");
    }

    @Test
    void throwsUnauthorizedWhenTokenHasNoUserIdClaim() {
        // A token minted without the userId claim (defensive scenario)
        String token = jwtService.generateToken("no-claim@shopsphere.test", null, List.of("CUSTOMER"));
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername("no-claim@shopsphere.test").password("n/a").roles("CUSTOMER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities()));

        assertThatThrownBy(provider::getCurrentUserId)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("user ID claim");
    }

    // ── JwtService round-trips ──────────────────────────────────────────

    @Test
    void jwtRoundTripPreservesSubjectUserIdAndRoles() {
        String token = jwtService.generateToken(
                "roundtrip@shopsphere.test", 42L, List.of("CUSTOMER", "ADMIN"));

        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("roundtrip@shopsphere.test");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRoles(token)).containsExactly("CUSTOMER", "ADMIN");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken("tamper@shopsphere.test", 1L, List.of("CUSTOMER"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        // A JWT signed 2 hours ago with a 1-hour expiry is already expired.
        String token = io.jsonwebtoken.Jwts.builder()
                .subject("expired@shopsphere.test")
                .claim("userId", 1L)
                .claim("roles", List.of("CUSTOMER"))
                .issuedAt(new java.util.Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new java.util.Date(System.currentTimeMillis() - 3_600_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(
                                "ZGV2LW9ubHktc2VjcmV0LWtleS1mb3Itc2hvcHNwaGVyZS10ZXN0cy0yNTYtYml0cy1taW5pbXVtLWtleS1tYXRlcmlhbA==")))
                .compact();

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    private User persistUser(String email) {
        User user = new User();
        user.setName("Provider Test User");
        user.setEmail(email);
        user.setPasswordHash("$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG");
        Role customer = new Role();
        customer.setRoleName("CUSTOMER");
        user.getRoles().add(customer);
        User saved = userRepository.save(user);
        createdEmails.add(email);
        return saved;
    }
}
