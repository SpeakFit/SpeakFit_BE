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

    // 3. 발표별 변화 추이 (차트를 그릴 수 있도록 리스트 구조로 교정)
    @Getter @Builder
    public static class GrowthTrend {
        private List<TrendPoint> speed; // 속도 변화 추이 리스트
        private List<TrendPoint> db;    // 성량 변화 추이 리스트
        private List<TrendPoint> pause; // 멈춤 변화 추이 리스트
        private List<TrendPoint> zcr;   // 발음 변화 추이 리스트
        private List<TrendPoint> hz;    // 강조 변화 추이 리스트
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

    // 날짜별 차트 좌표용 핵심 데이터 스펙 추가
    @Getter @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendPoint {
        private String date;  // "2026-05-22" 포맷의 날짜 문자열
        private double value; // 해당 날짜의 지표 평균값
    }

}