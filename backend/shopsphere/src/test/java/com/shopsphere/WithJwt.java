package com.shopsphere;

import com.shopsphere.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test annotation that installs an authenticated {@link SecurityContext} whose
 * credentials are a real JWT minted by {@link JwtService}, matching how
 * {@code JwtAuthenticationFilter} populates the context in production. This is
 * what lets {@code SecurityContextCurrentUserProvider} resolve the user id in
 * MockMvc tests.
 * <p>
 * Usage: {@code @WithJwt(email = "...", userId = 1L, roles = {"CUSTOMER"})}
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithJwt.Factory.class)
public @interface WithJwt {

    String email();

    long userId();

    String[] roles() default {"CUSTOMER"};

    class Factory implements WithSecurityContextFactory<WithJwt> {

        @Autowired
        private JwtService jwtService;

        @Override
        public SecurityContext createSecurityContext(WithJwt annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            String token = jwtService.generateToken(
                    annotation.email(), annotation.userId(),
                    java.util.List.of(annotation.roles()));
            UserDetails principal = org.springframework.security.core.userdetails.User
                    .withUsername(annotation.email())
                    .password("n/a")
                    .roles(annotation.roles())
                    .build();
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            principal, token, principal.getAuthorities());
            context.setAuthentication(authentication);
            return context;
        }
    }
}
