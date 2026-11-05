package com.shopsphere.service;

import com.shopsphere.dto.RegisterRequestDTO;
import com.shopsphere.dto.RegisterResponseDTO;
import com.shopsphere.entity.Cart;
import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.entity.Wishlist;
import com.shopsphere.exception.EmailAlreadyExistsException;
import com.shopsphere.repository.RoleRepository;
import com.shopsphere.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration (TASK: registration API).
 * <p>
 * The whole flow runs inside one transaction: the User, its Cart and its
 * Wishlist are persisted together (Cart/Wishlist cascade from User via
 * CascadeType.ALL). Duplicate emails race on the unique index, so the
 * {@link DataIntegrityViolationException} catch is the authoritative
 * duplicate check — the pre-check is only a fast-path UX improvement.
 */
@Service
public class AuthService {

    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO request) {
        String email = request.email() == null ? null : request.email().trim();

        // Fast-path duplicate check (concurrent requests are still caught
        // by the unique constraint + DataIntegrityViolationException below).
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Role customerRole = roleRepository.findByRoleName(CUSTOMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "CUSTOMER role not found in database"));

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Normalize: store blank phone as null.
        user.setPhone(request.phone() == null || request.phone().isBlank()
                ? null : request.phone().trim());

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
        } catch (DataIntegrityViolationException ex) {
            // Distinguish a duplicate email (users.email unique key) from
            // any other integrity failure instead of blanket-labelling it.
            if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
                throw new EmailAlreadyExistsException(email);
            }
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
}
