package com.speakfit.backend.global.config;

import com.speakfit.backend.global.config.security.RestAccessDeniedHandler;
import com.speakfit.backend.global.config.security.RestAuthenticationEntryPoint;
import com.speakfit.backend.global.infra.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 SecurityFilterChain 을 올려 401/403 응답과 permitAll 범위를 검증한다. (DB 불필요) */
@WebMvcTest(controllers = SecurityConfigIntegrationTest.ProbeController.class)
// 메인 애플리케이션 클래스(@EnableJpaAuditing)를 로드하지 않도록 필요한 빈만 명시한다.
@ContextConfiguration(classes = {
        SecurityConfigIntegrationTest.ProbeController.class,
        SecurityConfigIntegrationTest.TestBeans.class,
        SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=https://front.example.com",
        "app.cors.allowed-origin-patterns="
})
class SecurityConfigIntegrationTest {

    @RestController
    static class ProbeController {
        @GetMapping("/api/probe")
        String probe() { return "ok"; }
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        JwtProvider jwtProvider() {
            return new JwtProvider("test-secret-key-for-security-config-32bytes!", 3600, 86400);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    @DisplayName("토큰 없이 보호된 API 를 호출하면 401 + ApiResponse")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get("/api/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("COMMON401")));
    }

    @Test
    @DisplayName("access 토큰이면 통과한다")
    void accessTokenIs200() throws Exception {
        mockMvc.perform(get("/api/probe")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, "a@b.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh / ws 토큰을 Bearer 로 보내면 401")
    void nonAccessTokensAre401() throws Exception {
        mockMvc.perform(get("/api/probe")
                        .header("Authorization", "Bearer " + jwtProvider.createRefreshToken(1L)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/probe")
                        .header("Authorization", "Bearer " + jwtProvider.createPracticeWebSocketToken(1L, 1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공개 경로는 인증 없이 접근 가능(401 아님), 그 외 /auth 하위는 인증 필요")
    void publicPathsAreMinimal() throws Exception {
        // 핸들러가 없어 404 이지만 401 이 아니면 시큐리티 단계는 통과한 것
        mockMvc.perform(post("/auth/login")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/signup")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/refresh")).andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/logout")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/terms")).andExpect(status().isNotFound());

        mockMvc.perform(post("/auth/anything-else")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/login")).andExpect(status().isUnauthorized());
    }
}
