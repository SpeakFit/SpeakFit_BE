package com.speakfit.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    @Value("${app.cookie.secure}")
    private boolean secure;

    // 운영(HTTPS, 프론트와 API 도메인이 다름): SameSite=None + Secure
    // 로컬(HTTP, 프론트와 API 모두 localhost 로 같은 사이트): Secure 없이 SameSite=None 은 브라우저가 거부하므로 Lax 를 사용한다.
    private String sameSite() {
        return secure ? "None" : "Lax";
    }

    public void addRefreshTokenCookie(HttpServletResponse response,
                                      String refreshToken,
                                      long maxAgeSeconds) {

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite(sameSite())
                .maxAge(maxAgeSeconds)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite(sameSite())
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
