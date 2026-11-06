package com.shopsphere.service;

import com.shopsphere.dto.LoginRequestDTO;
import com.shopsphere.dto.LoginResponseDTO;
import com.shopsphere.dto.LoginUserDTO;
import com.shopsphere.dto.RegisterRequestDTO;
import com.shopsphere.dto.RegisterResponseDTO;
import com.shopsphere.entity.Cart;
import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.entity.Wishlist;
import com.shopsphere.exception.EmailAlreadyExistsException;
import com.shopsphere.repository.RoleRepository;
import com.shopsphere.repository.UserRepository;
import com.shopsphere.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Handles user registration and login.
 * <p>
 * The whole flow runs inside one transaction: the User, its Cart and its
 * Wishlist are persisted together (Cart/Wishlist cascade from User via
 * CascadeType.ALL). Duplicate emails race on the unique index, so the
 * authoritative duplicate check is the database constraint. A
 * {@link DataIntegrityViolationException} is allowed to propagate out of
 * the failed transaction and is translated to HTTP 409 only for the
 * users.email unique key, in {@link GlobalExceptionHandler}.
 */
@Service
public class AuthService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";

    /** Public, uniform message for every authentication failure. */
    public static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO request) {
        String email = request.email() == null
                ? null
                : request.email().trim().toLowerCase(Locale.ROOT);

        // Fast-path duplicate check (concurrent requests are still caught
        // by the unique constraint + DataIntegrityViolationException below).
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        // Get-or-create keeps registration self-sufficient on a fresh schema
        // (the roles seed row normally comes from shopsphere_sample_data.sql).
        Role customerRole = roleRepository.findByRoleName(CUSTOMER_ROLE)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setRoleName(CUSTOMER_ROLE);
                    return roleRepository.save(role);
                });

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Normalize: store blank phone as null.
        user.setPhone(request.phone() == null || request.phone().isBlank()
                ? null
                : request.phone().trim());

        // Role is assigned server-side only — clients can never inject one.
        user.getRoles().add(customerRole);

        // Cart + Wishlist in the same transaction, both sides synchronized.
        Cart cart = new Cart();
        cart.setUser(user);
        user.setCart(cart);

        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user);
        user.setWishlist(wishlist);

        try {
            userRepository.save(user);
            userRepository.flush(); // surface constraint violations now, not at commit
        } catch (DataIntegrityViolationException ex) {
            // Do NOT translate inside the transaction: it may be rollback-only.
            // Rethrow so GlobalExceptionHandler can inspect the constraint name.
            throw ex;
        }

        return new RegisterResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRoles().stream()
                        .map(Role::getRoleName)
                        .sorted()
                        .toList());
    }

    /**
     * Authenticates a user with email + password and mints a JWT.
     * <p>
     * Every failure path — unknown email, wrong password, locked or disabled
     * account, internal provider error — is collapsed into the same public
     * {@link BadCredentialsException} so the response never reveals which part
     * of the credential pair was wrong. Any exception carrying the underlying
     * cause is avoided: the {@code message} of the rethrown exception is the
     * fixed constant, never {@code ex.getMessage()}.
     * <p>
     * Users with no roles at all are rejected before a token is issued: a
     * token with an empty {@code roles} claim would authenticate via the JWT
     * filter but authorize nothing, so failing fast with the same generic 401
     * is safer and simpler.
     */
    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginRequestDTO request) {
        // Normalize exactly as registration does, so "Jane@Example.COM "
        // matches the stored lowercase-trimmed email.
        String email = request.email() == null
                ? null
                : request.email().trim().toLowerCase(Locale.ROOT);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException ex) {
            // Covers BadCredentials, UsernameNotFound, Locked, Disabled,
            // CredentialsExpired, InternalAuthenticationServiceException, etc.
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        // authenticate() only proves the password; reload for the live roles.
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));

        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        List<String> roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .sorted()
                .toList();

        String token = jwtService.generateToken(user.getEmail(), user.getId(), roles);

        return new LoginResponseDTO(
                token,
                "Bearer",
                jwtService.getExpirationMs() / 1000,
                new LoginUserDTO(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getPhone(),
                        roles));
    }
}
