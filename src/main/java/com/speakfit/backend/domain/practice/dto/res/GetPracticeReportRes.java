package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.practice.enums.PracticeIssueType;
import com.speakfit.backend.domain.practice.enums.SpeechInformation;
import com.speakfit.backend.domain.practice.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class GetPracticeReportRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private Status status;
        private String audioUrl;
        private Double time;
        private LocalDateTime createdAt;
        private String message; // 분석 중일 때 메시지

        private AudienceType audienceType;
        private AudienceUnderstanding audienceUnderstanding;
        private SpeechInformation speechInformation;

        private AnalysisDetail analysis;
        private AiAnalysisDetail aiAnalysis;
        private List<PracticeIssueRes> practiceIssues;
        private List<SentenceRes> sentences;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisDetail {
        private StatInfo wpm;
        private StatInfo pitch;
        private StatInfo intensity;
        private StatInfo zcr;
        private PauseInfo pause;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatInfo {
        private Double avg;
        private Double diff;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PauseInfo {
        private Double ratio;
        private Integer count;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PracticeIssueRes {
        private Long scriptSentenceId;
        private Integer sentenceIndex;
        private String sentenceText;
        private Integer startIndex;
        private Integer endIndex;
        private PracticeIssueType issueType;
        private String issueSummary;
        private String feedbackContent;
        private String reason;
        private Double score;
        private Integer displayOrder;
        private Double wpm;
        private Double intensity;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentenceRes {
        private Long scriptSentenceId;
        private Integer index;
        private String text;
        private Double startTime;
        private Double endTime;
        private Long startMs;
        private Long endMs;
        private Integer wordCount;
        private Integer skippedWordCount;
        private Double wpm;
        private Long pauseDurationMs;
        private Double avgPitch;
        private Double avgIntensity;
        private Double score;
        private String status;
    }
}
