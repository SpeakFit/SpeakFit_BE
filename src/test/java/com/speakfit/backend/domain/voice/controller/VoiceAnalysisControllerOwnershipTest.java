package com.speakfit.backend.domain.voice.controller;

import com.speakfit.backend.domain.voice.dto.res.VoiceAnalysisResultRes;
import com.speakfit.backend.domain.voice.exception.VoiceException;
import com.speakfit.backend.domain.voice.exception.VoiceExceptionStatus;
import com.speakfit.backend.domain.voice.service.VoiceAnalysisService;
import com.speakfit.backend.global.apiPayload.exception.ExceptionAdvice;
import com.speakfit.backend.global.config.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 컨트롤러가 로그인한 사용자의 id 를 서비스로 넘기는지 확인한다. */
class VoiceAnalysisControllerOwnershipTest {

    private final VoiceAnalysisService service = mock(VoiceAnalysisService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VoiceAnalysisController(service))
                .setControllerAdvice(new ExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthPrincipal(7L, "a@b.com"), null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("조회 시 로그인한 사용자의 id 가 서비스로 전달된다")
    void passesCurrentUserId() throws Exception {
        when(service.getVoiceAnalysisResult(10L, 7L)).thenReturn(
                VoiceAnalysisResultRes.builder().analysisId(10L).status("COMPLETED").progress(100).build());

        mockMvc.perform(get("/api/voice-analysis/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.analysisId").value(10));

        verify(service).getVoiceAnalysisResult(10L, 7L);
    }

    @Test
    @DisplayName("본인 것이 아니면 404 + VOICE404 로 응답한다")
    void notOwnedIs404() throws Exception {
        when(service.getVoiceAnalysisResult(10L, 7L))
                .thenThrow(new VoiceException(VoiceExceptionStatus.VOICE_ANALYSIS_NOT_FOUND));

        mockMvc.perform(get("/api/voice-analysis/10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VOICE404"));
    }
}
