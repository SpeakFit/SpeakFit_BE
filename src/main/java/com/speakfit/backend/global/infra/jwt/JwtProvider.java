package com.speakfit.backend.global.infra.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtProvider {

    // 토큰 종류 구분용 클레임. Python 분석 서버가 ws_practice 값을 그대로 검증하므로 값 변경 금지.
    public static final String TYPE_CLAIM = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String TYPE_WS_PRACTICE = "ws_practice";

    // HS256 최소 키 길이(256bit)
    private static final int MIN_SECRET_BYTES = 32;

    private final Key key;
    private final long accessExpSeconds;
    private final long refreshExpSeconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-exp-seconds}") long accessExpSeconds,
            @Value("${jwt.refresh-token-exp-seconds}") long refreshExpSeconds
    ){
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret(JWT_SECRET)은 최소 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. (현재 "
                            + secretBytes.length + "바이트)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessExpSeconds = accessExpSeconds;
        this.refreshExpSeconds = refreshExpSeconds;
    }

    // 토큰 생성
    public String createAccessToken(Long userId, String email) {
        return createToken(userId, email, accessExpSeconds, TYPE_ACCESS);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, null, refreshExpSeconds, TYPE_REFRESH);
    }

    public String createPracticeWebSocketToken(Long userId, Long practiceId) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, TYPE_WS_PRACTICE)
                .claim("practiceId", practiceId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 토큰 발급
    public Instant getRefreshTokenExpiresAt() {
        return Instant.now().plusSeconds(refreshExpSeconds);
    }

    private String createToken(Long userId, String email, long expSeconds, String type) {
        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expSeconds)));

        if (email != null) {
            builder.claim("email", email);
        }

        return builder
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public String getEmail(String token){
        Claims claims = parseClaims(token);
        Object v = claims.get("email");
        return v == null ? null : String.valueOf(v);
    }

    public String getType(String token) {
        Object v = parseClaims(token).get(TYPE_CLAIM);
        return v == null ? null : String.valueOf(v);
    }

    // API 인증에는 access 토큰만 허용한다. (refresh / ws_practice 토큰은 Bearer 인증에 사용할 수 없다)
    public boolean validateAccessToken(String token) {
        return hasType(token, TYPE_ACCESS);
    }

    // 토큰 재발급에는 refresh 토큰만 허용한다.
    public boolean validateRefreshToken(String token) {
        return hasType(token, TYPE_REFRESH);
    }

    private boolean hasType(String token, String expectedType) {
        try {
            return expectedType.equals(getType(token));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
