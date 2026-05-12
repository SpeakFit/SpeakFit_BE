package com.speakfit.backend.domain.voice.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PythonVoiceAnalysisRes {

    @JsonProperty("avg_pitch")
    private Double avgPitch;

    @JsonProperty("avg_wpm")
    private Double avgWpm;

    @JsonProperty("avg_intensity")
    private Double avgIntensity;

    @JsonProperty("avg_zcr")
    private Double avgZcr;

    @JsonProperty("pause_ratio")
    private Double pauseRatio;

    @JsonProperty("status")
    private String status;
}
