package com.speakfit.backend.global.config.security;

import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** 인증은 되었으나 접근 권한이 없는 요청에 대해 403 + ApiResponse 포맷으로 응답한다. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityErrorResponseWriter.write(response, jsonMapper, ErrorCode.FORBIDDEN);
    }
}
