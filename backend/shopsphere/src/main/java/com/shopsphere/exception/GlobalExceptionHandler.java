package com.shopsphere.exception;

import com.shopsphere.exception.EmailAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Runs outside the (now failed) transaction. Translates a violation of
     * the users.email unique key into 409; every other constraint failure
     * stays a generic 409-free 500 so we never mislabel it as a duplicate.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        if (isUniqueEmailViolation(ex)) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                    "Email already registered");
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Data integrity violation");
    }

    private boolean isUniqueEmailViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getRootCause();
        if (cause == null) {
            return false;
        }
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        return message.contains("users_email_key")        // H2 default name
                || (message.contains("users") && message.contains("email")
                        && message.contains("unique"))     // vendor-agnostic fallback
                || (message.contains("duplicate") && message.contains("email"));
    }

    /**
     * Uniform public response for every authentication failure raised inside
     * the application (login endpoint). The detail is a fixed constant from
     * {@link com.shopsphere.service.AuthService#INVALID_CREDENTIALS} — the
     * underlying exception message (e.g. "User not found") is never exposed.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                com.shopsphere.service.AuthService.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleDuplicateEmail(EmailAlreadyExistsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Inventory changed concurrently. Please retry your checkout.");
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ProblemDetail handleJpaOptimisticLock(OptimisticLockException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Inventory changed concurrently. Please retry your checkout.");
    }
}
