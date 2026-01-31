package com.speakfit.backend.domain.feedback.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetFeedbackDetailRes {
    private Long id;
    private String status;
    private String message;
    private String startDate;
    private String endDate;
    private ComparisonData comparisonData;
    private AnalysisHighlights analysisHighlights;
    private String aiFeedback;

    // 주차별 스피치 지표 비교 데이터
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ComparisonData {
        private Metrics thisWeek;
        private Metrics lastWeek;
    }

    // 발표 세부 지표 수치
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Metrics {
        private Double avgWpm;
        private Double avgPitch;
        private Double avgIntensity;
        private Double pauseRatio;
    }

    // 앞날 대비 지표 변화량 (차이값)
    @Getter
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AnalysisHighlights {
        private Double wpmDiff;
        private Double pauseCountDiff;
        private Double intensityDiff;
        private Double pitchDiff;
    }
}