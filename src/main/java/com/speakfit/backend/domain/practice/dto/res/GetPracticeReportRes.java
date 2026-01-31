package com.speakfit.backend.domain.practice.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // null인 필드 제외
public class GetPracticeReportRes {

    private Long practiceId;    // practiceId
    private String status;
    private String message;
    private String audioUrl;
    private Double time;
    private LocalDateTime createdAt;

    private AnalysisDetail analysis;
    private AiAnalysisDetail aiAnalysis;

    // --- 내부 클래스 ---
    @Getter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class AnalysisDetail {
        private StatInfo wpm; // 발화속도
        private StatInfo pitch; // 음높이
        private StatInfo intensity; // 성량
        private StatInfo zcr; // 발음 선명도
        private PauseInfo pause; // 쉼
    }

    // wpm,pitch,intensity,zcr 평균,변화폭 명시를 위한 클래스
    @Getter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class StatInfo {
        private Double avg;
        private Double variability;
    }

    // 쉼 비율, 쉼 횟수
    @Getter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class PauseInfo {
        private Double ratio;
        private Integer count;
    }

    // ai 총평
    @Getter @Builder @AllArgsConstructor @NoArgsConstructor
    public static class AiAnalysisDetail {
        private String summary;
        private LocalDateTime createdAt;
    }
}