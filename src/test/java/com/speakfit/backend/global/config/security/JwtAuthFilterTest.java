package com.speakfit.backend.global.config.security;

import com.speakfit.backend.global.infra.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private final JwtProvider jwtProvider =
            new JwtProvider("test-secret-key-for-jwt-auth-filter-32bytes!", 3600, 86400);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtProvider);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Authentication run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chained = {false};
        filter.doFilter(request, response, (req, res) -> chained[0] = true);
        assertThat(chained[0]).as("필터 체인은 항상 이어져야 한다").isTrue();
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("유효한 access 토큰이면 AuthPrincipal 로 인증된다")
    void accessTokenAuthenticates() throws Exception {
        Authentication auth = run("Bearer " + jwtProvider.createAccessToken(7L, "a@b.com"));

        assertThat(auth).isNotNull();
        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        assertThat(principal.getUserId()).isEqualTo(7L);
        assertThat(principal.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("refresh 토큰을 Bearer 로 보내면 인증되지 않는다")
    void refreshTokenDoesNotAuthenticate() throws Exception {
        assertThat(run("Bearer " + jwtProvider.createRefreshToken(7L))).isNull();
    }

    @Test
    @DisplayName("WebSocket 토큰을 Bearer 로 보내면 인증되지 않는다")
    void webSocketTokenDoesNotAuthenticate() throws Exception {
        assertThat(run("Bearer " + jwtProvider.createPracticeWebSocketToken(7L, 1L))).isNull();
    }

    @Test
    @DisplayName("토큰이 없거나 깨진 토큰이면 인증되지 않는다")
    void missingOrMalformedTokenDoesNotAuthenticate() throws Exception {
        assertThat(run(null)).isNull();
        assertThat(run("Bearer not-a-jwt")).isNull();
    }
}
