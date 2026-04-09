package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.practice.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class GetPracticeReportRes {
    private Long practiceId;
    private Status status; // String -> Status 변경
    private String message;
    private String audioUrl;
    private Double time;
    private LocalDateTime createdAt;
    private AnalysisDetail analysis;
    private AiAnalysisDetail aiAnalysis;

    @Getter @Builder @AllArgsConstructor
    public static class AnalysisDetail {
        private StatInfo wpm;
        private StatInfo pitch;
        private StatInfo intensity;
        private StatInfo zcr;
        private PauseInfo pause;
    }

    @Getter @Builder @AllArgsConstructor
    public static class StatInfo {
        private Double avg;
        private Double variability;
    }

    @Getter @Builder @AllArgsConstructor
    public static class PauseInfo {
        private Double ratio;
        private Integer count;
    }

    @Getter @Builder @AllArgsConstructor
    public static class AiAnalysisDetail {
        private String summary;
        private LocalDateTime createdAt;
    }
}
