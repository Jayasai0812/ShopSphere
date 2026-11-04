package com.shopsphere;

import com.shopsphere.common.HttpHeaderCurrentUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserProviderTest {

    private HttpHeaderCurrentUserProvider providerWith(String headerValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-User-Id")).thenReturn(headerValue);
        return new HttpHeaderCurrentUserProvider(request);
    }

    @Test
    void returnsUserIdForValidHeader() {
        assertThat(providerWith("1").getCurrentUserId()).isEqualTo(1L);
    }

    @Test
    void returnsLargeValidUserId() {
        assertThat(providerWith("999").getCurrentUserId()).isEqualTo(999L);
    }

    @Test
    void throwsBadRequestWhenHeaderMissing() {
        assertThatThrownBy(() -> providerWith(null).getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing required header");
    }

    @Test
    void throwsBadRequestWhenHeaderBlank() {
        assertThatThrownBy(() -> providerWith("   ").getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Missing required header");
    }

    @Test
    void throwsBadRequestWhenHeaderNotNumeric() {
        assertThatThrownBy(() -> providerWith("abc").getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("valid numeric");
    }

    @Test
    void throwsBadRequestWhenUserIdIsZero() {
        assertThatThrownBy(() -> providerWith("0").getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void throwsBadRequestWhenUserIdIsNegative() {
        assertThatThrownBy(() -> providerWith("-5").getCurrentUserId())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("positive");
    }
}
