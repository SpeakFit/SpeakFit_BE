package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.practice.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class StopPracticeRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private Status status;
        private String audioUrl;
    }
}
