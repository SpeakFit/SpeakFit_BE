package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.style.enums.StyleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class InputPracticeInfoRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private RecommendedStyle recommendedStyle;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedStyle {
        private Long styleId;
        private StyleType styleType;
        private String description;
    }
}
