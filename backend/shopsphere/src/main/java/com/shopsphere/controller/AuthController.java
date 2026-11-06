package com.shopsphere.controller;

import com.shopsphere.dto.LoginRequestDTO;
import com.shopsphere.dto.LoginResponseDTO;
import com.shopsphere.dto.RegisterRequestDTO;
import com.shopsphere.dto.RegisterResponseDTO;
import com.shopsphere.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(
            @Valid @RequestBody RegisterRequestDTO request) {
        RegisterResponseDTO response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates and returns a signed JWT. Authentication failures
     * (BadCredentialsException with a fixed, non-revealing message) are
     * translated to 401 by Spring Security / the exception handlers — the
     * endpoint itself never inspects the failure reason.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
