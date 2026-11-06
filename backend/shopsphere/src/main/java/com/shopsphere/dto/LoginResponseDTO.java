package com.shopsphere.dto;

/**
 * Response payload for a successful login.
 *
 * @param token     the signed JWT; the client sends it as
 *                  {@code Authorization: Bearer <token>}
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the token expires. Sourced from the same
 *                  {@code jwt.expiration-ms} configuration used to mint the
 *                  token, so the reported lifetime always matches reality.
 * @param user      non-sensitive identity data (never the password or hash)
 */
public record LoginResponseDTO(
        String token,
        String tokenType,
        long expiresIn,
        LoginUserDTO user) {
}
