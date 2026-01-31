package com.speakfit.backend.domain.feedback.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

public class GenerateFeedbackReq {

    @Getter
    @NoArgsConstructor
    public static class Request {
        @NotNull(message = "시작 날짜는 필수입니다.")
        private LocalDate startDate;
        @NotNull(message = "종료 날짜는 필수입니다.")
        private LocalDate endDate;
    }
}