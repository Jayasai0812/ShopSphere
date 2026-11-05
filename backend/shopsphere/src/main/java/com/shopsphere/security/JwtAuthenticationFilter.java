package com.shopsphere.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts the {@code Authorization: Bearer <token>} header, validates the JWT
 * and populates the {@link SecurityContextHolder}. Requests without a token are
 * passed through untouched — the {@link com.shopsphere.config.SecurityConfig}
 * rules decide whether they are permitted (public endpoints) or rejected (401).
 * <p>
 * The user account is loaded from the database on every authenticated request
 * (per the TASK-013 architecture review) so role changes and account status
 * apply immediately.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
            CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(BEARER_PREFIX.length()).trim();

            if (jwtService.isTokenValid(token)) {
                String email = jwtService.extractEmail(token);
                if (email != null) {
                    try {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        // The validated JWT is kept as the credentials so that
                        // SecurityContextCurrentUserProvider can read the userId claim.
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, token, userDetails.getAuthorities());
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
                        // Token valid but account gone: leave the context empty → 401.
                        SecurityContextHolder.clearContext();
                    }
                }
            }
            // Invalid/expired token: leave the context empty. Protected endpoints
            // then return 401 via the entry point; public endpoints still work.
        }

        filterChain.doFilter(request, response);
    }
}
