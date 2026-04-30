package com.speakfit.backend.domain.feedback.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;

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

    private UserAverageMetrics userAverageMetrics; // 1. 5대 지표 평균 요약
    private StyleMatching styleMatching;           // 2. 스피치 스타일 분석
    private GrowthTrend growthTrend;               // 3. 발표별 변화 추이
    private AiReport aiReport;                     // 4. AI 분석 리포트(Good/Bad)
    private PracticeGuide practiceGuide;           // 5. AI 리포트 요약

    // 1. 5대 지표 평균 요약
    @Getter @Builder
    public static class UserAverageMetrics {
        private String avgSpeed;  // "125 wpm"
        private String avgDB;     // "75 dB"
        private String totalPauses; // "8 회"
        private String avgZCR;    // "88 %"
        private String avgHz;     // "130 Hz"
    }

    // 2. 스피치 스타일 분석
    @Getter @Builder
    public static class StyleMatching {
        private String mostSimilarStyle;
        private Integer matchingRate;
        private String description;
    }

    // 3. 발표별 변화 추이
    @Getter @Builder
    public static class GrowthTrend {
        private MetricDiff speed;
        private MetricDiff db;
        private MetricDiff pause;
        private MetricDiff zcr;
        private MetricDiff hz;
    }
    // 4. AI 분석 리포트(Good/Bad)
    @Getter @Builder
    public static class AiReport {
        private FeedbackDetail positiveFeedback;    // 잘하고 있는 점
        private FeedbackDetail improvementFeedback; // 보완이 필요한 점
    }

    // 5. AI 리포트 요약
    @Getter @Builder
    public static class PracticeGuide {
        private List<String> targetMetrics; // 개선 대상 지표 목록
        private String summary;
        private String nextStep;
    }

    // 상세 피드백
    @Getter @Builder
    public static class FeedbackDetail {
        private String title;
        private String description;
    }

    // 지표 차이
    @Getter @Builder
    public static class MetricDiff {
        private double current;
        private double previous;
        private String diff; // "+ 15wpm" 등
    }

}