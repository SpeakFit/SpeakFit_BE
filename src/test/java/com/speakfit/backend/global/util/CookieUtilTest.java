package com.speakfit.backend.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    private CookieUtil cookieUtil(boolean secure) {
        CookieUtil cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "secure", secure);
        return cookieUtil;
    }

    private String setCookie(MockHttpServletResponse response) {
        return response.getHeader("Set-Cookie");
    }

    @Test
    @DisplayName("운영(secure=true): SameSite=None; Secure; HttpOnly")
    void productionCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil(true).addRefreshTokenCookie(response, "token", 1209600);

        assertThat(setCookie(response))
                .contains("refreshToken=token", "HttpOnly", "Secure", "SameSite=None", "Path=/", "Max-Age=1209600");
    }

    @Test
    @DisplayName("로컬(secure=false): Secure 없이 SameSite=None 은 브라우저가 거부하므로 SameSite=Lax 로 발급한다")
    void localCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieUtil(false).addRefreshTokenCookie(response, "token", 1209600);

        assertThat(setCookie(response))
                .contains("refreshToken=token", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Secure")
                .doesNotContain("SameSite=None");
    }

    @Test
    @DisplayName("쿠키 삭제도 발급과 같은 속성(SameSite, Secure)으로 내려가야 브라우저가 지운다")
    void clearCookieUsesSameAttributes() {
        MockHttpServletResponse secure = new MockHttpServletResponse();
        cookieUtil(true).clearRefreshTokenCookie(secure);
        assertThat(setCookie(secure)).contains("Max-Age=0", "Secure", "SameSite=None");

        MockHttpServletResponse local = new MockHttpServletResponse();
        cookieUtil(false).clearRefreshTokenCookie(local);
        assertThat(setCookie(local)).contains("Max-Age=0", "SameSite=Lax").doesNotContain("Secure");
    }
}
