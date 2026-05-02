package com.speakfit.backend.domain.voice.dto.res;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoiceAnalysisResultRes {

    private Long analysisId;
    private String status;
    private Integer progress;
    private VoiceStyle voiceStyle;
    private UserAverageMetrics userAverageMetrics;

    @Getter
    @Builder
    public static class VoiceStyle {
        private String mostSimilarStyle;
        private Integer matchingRate;
        private String description;
    }

    @Getter
    @Builder
    public static class UserAverageMetrics {
        private Double avgPitch;
        private Double avgWPM;
    }
}