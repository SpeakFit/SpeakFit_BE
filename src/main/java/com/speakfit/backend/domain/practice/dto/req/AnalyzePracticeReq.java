package com.speakfit.backend.domain.practice.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AnalyzePracticeReq {

    @Getter
    @NoArgsConstructor
    public static class Request {
        @NotNull(message = "연습 기록 ID는 필수입니다.")
        private Long practiceId;
    }
}
