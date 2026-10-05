package com.speakfit.backend.global.apiPayload.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExceptionAdviceTest {

    @RestController
    static class ProbeController {
        @GetMapping("/missing")
        String missing() throws Exception {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing", "missing");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ExceptionAdvice())
            .build();

    @Test
    @DisplayName("존재하지 않는 경로는 500 이 아니라 404 + COMMON404 로 응답한다")
    void missingResourceIs404() throws Exception {
        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 기존처럼 500 으로 응답한다")
    void unexpectedExceptionIs500() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500"));
    }
}
