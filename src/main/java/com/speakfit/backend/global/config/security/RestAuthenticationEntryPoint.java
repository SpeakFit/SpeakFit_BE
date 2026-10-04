package com.speakfit.backend.global.config.security;

import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 인증되지 않은 요청(토큰 없음 / 만료 / 위조 / access 토큰이 아님)에 대해
 * 기본 동작(403) 대신 401 + ApiResponse 포맷으로 응답한다.
 * 프론트는 401을 받으면 토큰 재발급 후 재시도한다.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        SecurityErrorResponseWriter.write(response, jsonMapper, ErrorCode.UNAUTHORIZED);
    }
}
