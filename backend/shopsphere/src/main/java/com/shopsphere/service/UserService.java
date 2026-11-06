package com.shopsphere.service;

import com.shopsphere.common.CurrentUserProvider;
import com.shopsphere.dto.ChangePasswordRequestDTO;
import com.shopsphere.dto.UpdateProfileRequestDTO;
import com.shopsphere.dto.UserProfileResponseDTO;
import com.shopsphere.entity.Role;
import com.shopsphere.entity.User;
import com.shopsphere.exception.ResourceNotFoundException;
import com.shopsphere.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Profile management for the logged-in user (TASK-015).
 *
 * The user whose profile is read or updated is ALWAYS resolved from the JWT
 * security context via {@link CurrentUserProvider} — no request-supplied user
 * ID is ever honored. Password verification uses the BCrypt encoder against
 * the stored hash; raw passwords are never compared or logged. No response
 * produced by this service carries password information.
 */
@Service
public class UserService {

        private final UserRepository userRepository;
        private final CurrentUserProvider currentUserProvider;
        private final PasswordEncoder passwordEncoder;

        public UserService(UserRepository userRepository,
                        CurrentUserProvider currentUserProvider,
                        PasswordEncoder passwordEncoder) {
                this.userRepository = userRepository;
                this.currentUserProvider = currentUserProvider;
                this.passwordEncoder = passwordEncoder;
        }

        /** Loads the authenticated user's own profile. */
        @Transactional(readOnly = true)
        public UserProfileResponseDTO getProfile() {
                User user = loadCurrentUser();
                return toProfileDto(user);
        }

        /**
         * Updates the authenticated user's name and phone. Email is not
         * editable here; blank phone is normalized to null (clearing it).
         */
        @Transactional
        public UserProfileResponseDTO updateProfile(UpdateProfileRequestDTO request) {
                User user = loadCurrentUser();
                user.setName(request.name().trim());
                user.setPhone(request.phone() == null || request.phone().isBlank()
                                ? null
                                : request.phone().trim());
                return toProfileDto(userRepository.save(user));
        }

        /**
         * Changes the authenticated user's password. The current password is
         * verified with BCrypt first; a mismatch is reported as a field error
         * on currentPassword (400) without revealing anything about the stored
         * hash. The new password is BCrypt-encoded before persistence.
         * <p>
         * JWTs issued BEFORE this change remain valid until their natural
         * expiry — the token is stateless and cannot be revoked. A
         * token-version or denylist mechanism is the later hardening task.
         * <p>
         * Returns 204 No Content: the profile did not change, so there is
         * nothing to send back.
         */
        @Transactional
        public void changePassword(ChangePasswordRequestDTO request) {
                User user = loadCurrentUser();

                if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
                }

                user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
                userRepository.save(user);
        }

        private User loadCurrentUser() {
                Long userId = currentUserProvider.getCurrentUserId();
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                return user;
        }

        private UserProfileResponseDTO toProfileDto(User user) {
                return new UserProfileResponseDTO(
                                user.getId(),
                                user.getName(),
                                user.getEmail(),
                                user.getPhone(),
                                user.getRoles().stream().map(Role::getRoleName).sorted().toList());
        }
}
