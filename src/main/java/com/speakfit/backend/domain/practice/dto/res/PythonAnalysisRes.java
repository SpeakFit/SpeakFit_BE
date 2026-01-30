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

    // 2. AI 총평 요약
    private String aiSummary;
}