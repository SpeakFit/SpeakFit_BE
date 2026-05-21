package com.speakfit.backend.domain.metric.dto;

public record TargetMetricResult(
        Double targetWpm,
        Double targetPitch,
        Double targetIntensity,
        Double targetZcr,
        Double targetPauseRatio
) {
}
