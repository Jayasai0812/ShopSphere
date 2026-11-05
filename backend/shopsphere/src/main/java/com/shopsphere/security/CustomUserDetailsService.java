package com.shopsphere.security;

import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads the ShopSphere {@link User} by email for the authentication manager and
 * for the JWT filter. Loading on every authenticated request (as agreed in the
 * TASK-013 review) means disabled accounts and changed roles take effect
 * immediately instead of waiting for token expiry.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(Role::getRoleName)
                .map(role -> "ROLE_" + role)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toList());

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                authorities);
    }
}
