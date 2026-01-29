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

    private final Key key;
    private final long accessExpSeconds;
    private final long refreshExpSeconds;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-exp-seconds}") long accessExpSeconds,
            @Value("${jwt.refresh-token-exp-seconds}") long refreshExpSeconds
    ){
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpSeconds = accessExpSeconds;
        this.refreshExpSeconds = refreshExpSeconds;
    }

    // 토큰 생성
    public String createAccessToken(Long userId, String usersId) {
        return createToken(userId, usersId, accessExpSeconds);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, refreshExpSeconds);
    }

    // 토큰 발급
    public Instant getRefreshTokenExpiresAt() {
        return Instant.now().plusSeconds(refreshExpSeconds);
    }

    private String createToken(Long userId, String usersId, long expSeconds) {
        Instant now = Instant.now();

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expSeconds)));

        if (usersId != null) {
            builder.claim("usersId", usersId);
        }

        return builder
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long getUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public String getUsersId(String token){
        Claims claims = parseClaims(token);
        Object v = claims.get("usersId");
        return v == null ? null : String.valueOf(v);
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
