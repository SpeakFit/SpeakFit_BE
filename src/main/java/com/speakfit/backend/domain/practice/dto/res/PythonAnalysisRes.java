package com.speakfit.backend.domain.practice.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PythonAnalysisRes {
    @JsonProperty("durationSec")
    private Double durationSec;
    @JsonProperty("avgWpm")
    private Double avgWpm;
    @JsonProperty("avgPitch")
    private Double avgPitch;
    @JsonProperty("avgIntensity")
    private Double avgIntensity;                // dBFS — 내부 평가용 유지
    @JsonProperty("volumeScore")
    private Integer volumeScore;               // [STEP-D] 0~100 음량 점수 (표시용)
    @JsonProperty("volumeLevel")
    private String volumeLevel;                // [STEP-D] "작음" / "적정" / "큼" (표시용)
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
    // [STAGE 3] 출력 구조 확장 — 강점/핵심 개선 액션/연습 팁
    @JsonProperty("strengths")
    private String strengths;
    @JsonProperty("improvements")
    private String improvements;
    @JsonProperty("practiceTip")
    private String practiceTip;
    // [STAGE 4] 내용·구성 피드백
    @JsonProperty("contentSummary")
    private String contentSummary;
    @JsonProperty("contentFeedback")
    private String contentFeedback;

    @JsonProperty("wordResults")
    private List<WordResult> wordResults;
    @JsonProperty("sentenceResults")
    private List<SentenceResult> sentenceResults;
    @JsonProperty("issues")
    private List<IssueResult> issues;

    @Getter
    @NoArgsConstructor
    public static class WordResult {
        @JsonProperty("scriptWordId")
        private Long scriptWordId;
        @JsonProperty("wordIndex")
        private Integer wordIndex;
        @JsonProperty("globalWordIndex")
        private Integer globalWordIndex;
        @JsonProperty("sentenceWordIndex")
        private Integer sentenceWordIndex;
        @JsonProperty("scriptText")
        private String scriptText;
        @JsonProperty("spokenText")
        private String spokenText;
        @JsonProperty("startMs")
        private Long startMs;
        @JsonProperty("endMs")
        private Long endMs;
        @JsonProperty("confidence")
        private Double confidence;
        @JsonProperty("skipped")
        private Boolean skipped;
        @JsonProperty("status")
        private String status;

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
        @JsonProperty("mismatchWordCount")
        private Integer mismatchWordCount;
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
        private String status;
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
        private String issueType;
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
