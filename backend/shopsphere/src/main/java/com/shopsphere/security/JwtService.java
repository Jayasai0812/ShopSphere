package com.shopsphere.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * Generates and validates HS256 JSON Web Tokens.
 * <p>
 * Token layout (as agreed in the TASK-013 architecture review):
 * <ul>
 * <li>{@code sub} — the user's email (unique identifier)</li>
 * <li>{@code userId} claim — the database user id, so the current user can be
 * resolved without an extra lookup by consumers of the token</li>
 * <li>{@code roles} claim — list of role names, e.g. ["CUSTOMER"]</li>
 * </ul>
 * The signing secret is Base64-encoded key material supplied via configuration
 * (environment variable {@code JWT_SECRET}); it must be at least 256 bits.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Builds a signed access token for the given user.
     */
    public String generateToken(String email, Long userId, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a token. Returns the claims or throws
     * {@link JwtException} for any signature, format or expiry problem.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * The configured token lifetime in milliseconds. AuthService reports this
     * as {@code expiresIn} so the advertised expiry always matches the
     * lifetime baked into the tokens this service mints.
     */
    public long getExpirationMs() {
        return expirationMs;
    }

    /** Extracts the subject (email) from a valid token. */
    public String extractEmail(String token) {
        return parseToken(token).getSubject();
    }

    /** Extracts the userId claim from a valid token. */
    public Long extractUserId(String token) {
        Number userId = parseToken(token).get("userId", Number.class);
        return userId == null ? null : userId.longValue();
    }

    /** Extracts the roles claim from a valid token. */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parseToken(token).get("roles");
        return roles instanceof List ? (List<String>) roles : List.of();
    }

    /**
     * True if the token carries a valid signature and has not expired.
     * Signature/format errors are treated as invalid rather than fatal.
     */
    public boolean isTokenValid(String token) {
        try {
            return parseToken(token).getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
