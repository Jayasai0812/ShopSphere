package com.shopsphere.config;

import com.shopsphere.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security configuration for TASK-013.1 (security foundation).
 * <p>
 * Rules follow the review matrix: public browsing (health, product/category
 * reads) is open; everything that mutates catalog data requires ADMIN; carts,
 * orders and other customer endpoints require authentication. There is no CSRF
 * token (bearer-token API, no cookies) and no server-side session.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── public: health + auth endpoints ─────────────────
                        .requestMatchers("/api/v1/health", "/api/v1/auth/**").permitAll()
                        // ── public browsing: catalog reads ──────────────────
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**", "/api/v1/categories/**").permitAll()
                        // ── admin-only: catalog writes ──────────────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/products/**", "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/products/**", "/api/v1/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/products/**", "/api/v1/categories/**").hasRole("ADMIN")
                        // ── everything else: any authenticated user ─────────
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        // 401 with no redirect for unauthenticated API access
                        .authenticationEntryPoint(new HttpStatusEntryPoint(
                                org.springframework.http.HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS for the Angular dev server. Origins are dev-focused; tighten before
     * production deployment.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:4200"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
