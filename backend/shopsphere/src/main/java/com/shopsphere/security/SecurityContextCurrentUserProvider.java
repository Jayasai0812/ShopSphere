package com.shopsphere.security;

import com.shopsphere.common.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves the current user's id from the JWT claims held in the authenticated
 * principal's details. The client can no longer declare its own identity via a
 * header — identity comes only from the validated token and the security
 * context, closing the spoofing hole the reviewer flagged in TASK-012.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final JwtService jwtService;

    public SecurityContextCurrentUserProvider(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No authenticated user in security context");
        }

        // The raw credential (JWT) is preserved on the authentication token by
        // the filter's UserDetailsService lookup; extract the userId claim.
        Object credentials = authentication.getCredentials();
        if (credentials instanceof String token) {
            Long userId = jwtService.extractUserId(token);
            if (userId != null && userId > 0) {
                return userId;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Authenticated token does not carry a valid user ID claim");
    }
}
