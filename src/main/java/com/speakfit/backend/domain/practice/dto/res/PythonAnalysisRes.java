package com.speakfit.backend.domain.practice.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
