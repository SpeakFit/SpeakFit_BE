package com.speakfit.backend.domain.practice.dto.res;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PythonAnalysisRes {
    // 1. 정량 지표
    private Double avgWpm;
    private Double avgPitch;
    private Double avgIntensity;
    private Double avgZcr;
    private Double pauseRatio;
    private Double wpmDiff;
    private Double pitchDiff;
    private Double intensityDiff;
    private Double zcrDiff;
    private Integer pauseCount;

    // 2. AI 상세 피드백 및 총평
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
}
