package com.speakfit.backend.global.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("인증 실패는 403 이 아닌 401 + COMMON401 로 응답한다")
    void unauthenticatedIs401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(jsonMapper)
                .commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("COMMON401")
                .contains("인증이 필요합니다.");
    }

    @Test
    @DisplayName("권한 없음은 403 + COMMON403 로 응답한다")
    void accessDeniedIs403() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(jsonMapper)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("COMMON403");
    }
}
