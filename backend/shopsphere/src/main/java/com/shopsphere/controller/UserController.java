package com.shopsphere.controller;

import com.shopsphere.dto.ChangePasswordRequestDTO;
import com.shopsphere.dto.UpdateProfileRequestDTO;
import com.shopsphere.dto.UserProfileResponseDTO;
import com.shopsphere.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile endpoints for the authenticated user (TASK-015).
 *
 * All routes operate exclusively on the identity from the JWT security
 * context. There is deliberately no /{id} path variable and no user ID in
 * any request body — a client can never address another user's profile.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

        private final UserService userService;

        public UserController(UserService userService) {
                this.userService = userService;
        }

        /** Returns the logged-in user's profile. */
        @GetMapping("/me")
        public ResponseEntity<UserProfileResponseDTO> getProfile() {
                return ResponseEntity.ok(userService.getProfile());
        }

        /** Updates the logged-in user's name and phone. */
        @PutMapping("/me")
        public ResponseEntity<UserProfileResponseDTO> updateProfile(
                        @Valid @RequestBody UpdateProfileRequestDTO request) {
                return ResponseEntity.ok(userService.updateProfile(request));
        }

        /** Changes the logged-in user's password after verifying the current one. */
        @PatchMapping("/me/password")
        public ResponseEntity<UserProfileResponseDTO> changePassword(
                        @Valid @RequestBody ChangePasswordRequestDTO request) {
                return ResponseEntity.ok(userService.changePassword(request));
        }
}
