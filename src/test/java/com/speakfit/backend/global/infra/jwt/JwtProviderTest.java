package com.speakfit.backend.global.infra.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-32bytes!!";

    private JwtProvider provider() {
        return new JwtProvider(SECRET, 3600, 86400);
    }

    @Test
    @DisplayName("시크릿이 32바이트 미만이면 기동 단계에서 명확한 메시지로 실패한다")
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtProvider("too-short", 3600, 86400))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("access 토큰은 type=access 이고 API 인증에 사용할 수 있다")
    void accessTokenIsAccepted() {
        JwtProvider provider = provider();
        String token = provider.createAccessToken(7L, "a@b.com");

        assertThat(provider.getType(token)).isEqualTo(JwtProvider.TYPE_ACCESS);
        assertThat(provider.validateAccessToken(token)).isTrue();
        assertThat(provider.getUserId(token)).isEqualTo(7L);
        assertThat(provider.getEmail(token)).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("refresh 토큰은 API 인증(access)으로 통과하지 못한다")
    void refreshTokenIsRejectedAsAccess() {
        JwtProvider provider = provider();
        String token = provider.createRefreshToken(7L);

        assertThat(provider.getType(token)).isEqualTo(JwtProvider.TYPE_REFRESH);
        assertThat(provider.validate(token)).isTrue();
        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("WebSocket 토큰은 API 인증(access)으로 통과하지 못하고 Python 이 기대하는 type 값을 가진다")
    void webSocketTokenIsRejectedAsAccess() {
        JwtProvider provider = provider();
        String token = provider.createPracticeWebSocketToken(7L, 99L);

        assertThat(provider.getType(token)).isEqualTo("ws_practice");
        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("type 클레임이 없는 토큰(구버전 토큰)은 access 로 인정하지 않는다")
    void tokenWithoutTypeIsRejected() {
        String legacy = Jwts.builder()
                .subject("7")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider().validateAccessToken(legacy)).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰과 다른 키로 서명된 토큰은 거부한다")
    void expiredOrForgedTokenIsRejected() {
        JwtProvider expired = new JwtProvider(SECRET, -10, 86400);
        assertThat(provider().validateAccessToken(expired.createAccessToken(7L, "a@b.com"))).isFalse();

        JwtProvider other = new JwtProvider("another-secret-key-for-forged-token-32bytes", 3600, 86400);
        assertThat(provider().validateAccessToken(other.createAccessToken(7L, "a@b.com"))).isFalse();
    }
}
