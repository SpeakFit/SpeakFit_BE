package com.speakfit.backend.global.config.security;

import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 시큐리티 필터 단계(컨트롤러 이전)의 에러를 ApiResponse JSON으로 쓴다. */
final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {}

    static void write(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.onFailure(errorCode, null)));
    }
}
