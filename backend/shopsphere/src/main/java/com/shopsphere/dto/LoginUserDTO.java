package com.shopsphere.dto;

import java.util.List;

/**
 * The user-information section of a successful login response. Kept separate
 * from the token fields so clients can treat identity and credentials as
 * distinct concerns. Carries only non-sensitive data — never the password or
 * its hash.
 */
public record LoginUserDTO(
        Long id,
        String name,
        String email,
        String phone,
        List<String> roles) {
}
