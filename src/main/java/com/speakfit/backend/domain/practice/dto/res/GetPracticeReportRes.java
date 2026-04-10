package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.practice.enums.DetailStatus;
import com.speakfit.backend.domain.practice.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class GetPracticeReportRes {
    private Long practiceId;
    private Status status;
    private String message;
    private String audioUrl;
    private Double time;
    private LocalDateTime createdAt;
    
    private AnalysisDetail analysis;
    private AiAnalysisDetail aiAnalysis;
    private List<PracticeIssueRes> practiceIssues;
    private List<SentenceRes> sentences;

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
        private String aiSummary;
        private String wpmSummary;
        private String wpmFeedback;
        private String energySummary;
        private String energyFeedback;
        private String pauseFeedback;
        private String symbolFeedback;
        private Double goalSimilarityScore;
        private String goalSummary;
        private String goalFeedback;
        private LocalDateTime createdAt;
    }

    @Getter @Builder @AllArgsConstructor
    public static class PracticeIssueRes {
        private Integer startIndex;
        private Integer endIndex;
        private String issueSummary;
        private String feedbackContent;
        private Double wpm;
        private Double intensity;
    }

    @Getter @Builder @AllArgsConstructor
    public static class SentenceRes {
        private Integer index;
        private String text;
        private Double startTime;
        private Double endTime;
        private DetailStatus status; // String -> DetailStatus Enum 변경
    }
}
