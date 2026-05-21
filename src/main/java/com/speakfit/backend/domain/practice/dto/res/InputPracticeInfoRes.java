package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.style.enums.StyleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class InputPracticeInfoRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private List<StyleItem> styleList;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleItem {
        private Long styleId;
        private StyleType styleType;
        private String displayName;
        private String description;
        private String guideAudioUrl;
        private TargetMetrics targetMetrics;
        private Boolean isRecommended;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetMetrics {
        private Double targetWpm;
        private Double targetPitch;
        private Double targetIntensity;
        private Double targetZcr;
        private Double targetPauseRatio;
    }
}
