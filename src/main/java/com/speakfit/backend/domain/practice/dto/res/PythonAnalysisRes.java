package com.speakfit.backend.domain.practice.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.speakfit.backend.domain.practice.enums.DetailStatus;
import com.speakfit.backend.domain.practice.enums.PracticeIssueType;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PythonAnalysisRes {
    @JsonProperty("avgWpm")
    private Double avgWpm;
    @JsonProperty("avgPitch")
    private Double avgPitch;
    @JsonProperty("avgIntensity")
    private Double avgIntensity;
    @JsonProperty("avgZcr")
    private Double avgZcr;
    @JsonProperty("pauseRatio")
    private Double pauseRatio;
    @JsonProperty("wpmDiff")
    private Double wpmDiff;
    @JsonProperty("pitchDiff")
    private Double pitchDiff;
    @JsonProperty("intensityDiff")
    private Double intensityDiff;
    @JsonProperty("zcrDiff")
    private Double zcrDiff;
    @JsonProperty("pauseCount")
    private Integer pauseCount;

    @JsonProperty("aiSummary")
    private String aiSummary;
    @JsonProperty("wpmSummary")
    private String wpmSummary;
    @JsonProperty("wpmFeedback")
    private String wpmFeedback;
    @JsonProperty("energySummary")
    private String energySummary;
    @JsonProperty("energyFeedback")
    private String energyFeedback;
    @JsonProperty("pauseFeedback")
    private String pauseFeedback;
    @JsonProperty("symbolFeedback")
    private String symbolFeedback;
    @JsonProperty("goalSimilarityScore")
    private Double goalSimilarityScore;
    @JsonProperty("goalSummary")
    private String goalSummary;
    @JsonProperty("goalFeedback")
    private String goalFeedback;

    @JsonProperty("wordResults")
    private List<WordResult> wordResults;
    @JsonProperty("sentenceResults")
    private List<SentenceResult> sentenceResults;
    @JsonProperty("issues")
    private List<IssueResult> issues;

    @Getter
    @NoArgsConstructor
    public static class WordResult {
        @JsonProperty("wordIndex")
        private Integer wordIndex;
        @JsonProperty("globalWordIndex")
        private Integer globalWordIndex;
        @JsonProperty("sentenceWordIndex")
        private Integer sentenceWordIndex;
        @JsonProperty("startMs")
        private Long startMs;
        @JsonProperty("endMs")
        private Long endMs;
        @JsonProperty("confidence")
        private Double confidence;
        @JsonProperty("skipped")
        private Boolean skipped;
        @JsonProperty("status")
        private DetailStatus status;

        // Python 응답 단어 인덱스 해석 구현
        public Integer getResolvedGlobalWordIndex() {
            if (globalWordIndex != null) {
                return globalWordIndex;
            }

            return wordIndex;
        }
    }

    @Getter
    @NoArgsConstructor
    public static class SentenceResult {
        @JsonProperty("scriptSentenceId")
        private Long scriptSentenceId;
        @JsonProperty("sentenceIndex")
        private Integer sentenceIndex;
        @JsonProperty("startMs")
        private Long startMs;
        @JsonProperty("endMs")
        private Long endMs;
        @JsonProperty("wordCount")
        private Integer wordCount;
        @JsonProperty("skippedWordCount")
        private Integer skippedWordCount;
        @JsonProperty("wpm")
        private Double wpm;
        @JsonProperty("pauseDurationMs")
        private Long pauseDurationMs;
        @JsonProperty("avgPitch")
        private Double avgPitch;
        @JsonProperty("avgIntensity")
        private Double avgIntensity;
        @JsonProperty("score")
        private Double score;
        @JsonProperty("status")
        private DetailStatus status;
    }

    @Getter
    @NoArgsConstructor
    public static class IssueResult {
        @JsonProperty("scriptSentenceId")
        private Long scriptSentenceId;
        @JsonProperty("sentenceIndex")
        private Integer sentenceIndex;
        @JsonProperty("startIndex")
        private Integer startIndex;
        @JsonProperty("endIndex")
        private Integer endIndex;
        @JsonProperty("issueType")
        private PracticeIssueType issueType;
        @JsonProperty("issueSummary")
        private String issueSummary;
        @JsonProperty("feedbackContent")
        private String feedbackContent;
        @JsonProperty("reason")
        private String reason;
        @JsonProperty("score")
        private Double score;
        @JsonProperty("displayOrder")
        private Integer displayOrder;
        @JsonProperty("wpm")
        private Double wpm;
        @JsonProperty("intensity")
        private Double intensity;
    }
}
