package com.speakfit.backend.domain.guide.dto.res;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendStyleRes {
    private Long baselineVoiceId; // 맵핑된 유저의 베이스라인 고유 식별자
    private AudienceInfo audienceInfo;
    private List<RecommendedStyle> recommendedStyles;

    @Getter
    @Builder
    public static class AudienceInfo {
        private String targetWpmRange;
        private String targetPitchVariance;
        private String targetPauseRatio;
    }

    @Getter
    @Builder
    public static class RecommendedStyle {
        private String styleName;
        private Double matchingRate;
        private boolean isBest;
        private String description;
    }
}