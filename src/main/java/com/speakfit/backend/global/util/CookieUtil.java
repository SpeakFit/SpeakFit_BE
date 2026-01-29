package com.speakfit.backend.global.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public void addRefreshTokenCookie(HttpServletResponse response,
                                      String refreshToken,
                                      long maxAgeSeconds,
                                      boolean secure){

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response, boolean secure){
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
