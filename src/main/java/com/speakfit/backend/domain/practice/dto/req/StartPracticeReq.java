package com.speakfit.backend.domain.practice.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class StartPracticeReq {
    @Getter
    @NoArgsConstructor
    public static class Request {
        @NotNull(message="대본 ID는 필수입니다.")
        private Long scriptId;
    }
}
