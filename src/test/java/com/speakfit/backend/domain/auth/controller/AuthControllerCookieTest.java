package com.speakfit.backend.domain.auth.controller;

import com.speakfit.backend.domain.auth.dto.res.LoginRes;
import com.speakfit.backend.domain.auth.exception.AuthErrorCode;
import com.speakfit.backend.domain.auth.service.AuthService;
import com.speakfit.backend.global.apiPayload.exception.CustomException;
import com.speakfit.backend.global.apiPayload.exception.ExceptionAdvice;
import com.speakfit.backend.global.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /auth/refresh, /auth/logout 의 쿠키 처리와 응답 코드를 검증한다. */
class AuthControllerCookieTest {

    private final AuthService authService = mock(AuthService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CookieUtil cookieUtil = new CookieUtil();
        ReflectionTestUtils.setField(cookieUtil, "secure", true);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, cookieUtil))
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("refresh 성공: 새 access 토큰을 본문으로, 새 refresh 토큰을 HttpOnly 쿠키로 내려준다")
    void refreshSuccessSetsNewCookie() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(LoginRes.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .refreshTokenMaxAgeSeconds(1209600)
                .build());

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").value("new-access"))
                // refresh 토큰은 본문에 노출되지 않는다
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("new-refresh"))))
                .andExpect(header().string("Set-Cookie", containsString("refreshToken=new-refresh")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")));
    }

    @Test
    @DisplayName("refresh 실패: 401 + AUTH401_2 이고 쓸 수 없는 쿠키를 제거(Max-Age=0)한다")
    void refreshFailureClearsCookie() throws Exception {
        when(authService.refresh("bad")).thenThrow(new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refreshToken", "bad")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_2"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("refresh: 쿠키가 없어도 서비스로 위임되어 401 로 응답한다")
    void refreshWithoutCookie() throws Exception {
        when(authService.refresh(null)).thenThrow(new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout: 서비스에 토큰 폐기를 요청하고 쿠키를 제거한다")
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refreshToken", "some-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(authService).logout("some-refresh");
    }

    @Test
    @DisplayName("logout: 쿠키가 없어도 200 (멱등)")
    void logoutWithoutCookieIsOk() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isOk());
        verify(authService).logout(null);
    }
}
