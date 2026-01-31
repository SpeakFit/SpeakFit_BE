package com.speakfit.backend.domain.feedback.dto.req;

import lombok.Getter;
import java.time.LocalDate;

public class GenerateFeedbackReq {

    @Getter
    public static class Request {
        private LocalDate startDate;
        private LocalDate endDate;
    }
}