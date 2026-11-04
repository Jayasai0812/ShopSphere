package com.shopsphere.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequestScope
public class HttpHeaderCurrentUserProvider implements CurrentUserProvider {

    private static final String HEADER = "X-User-Id";

    private final HttpServletRequest request;

    public HttpHeaderCurrentUserProvider(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public Long getCurrentUserId() {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing required header: " + HEADER);
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        HEADER + " must be a positive number");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    HEADER + " must be a valid numeric user ID");
        }
    }
}
