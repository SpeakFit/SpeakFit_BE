package com.speakfit.backend.domain.guide.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateGuideRes {
    private Long guideId;
    private Long practiceRecordId;
    private String selectedStyle;
    private FinalDeliveryGuides finalDeliveryGuides;

    @Getter @Builder
    public static class FinalDeliveryGuides {
        private MetricDetail wpm;
        private MetricDetail pitch;
        private MetricDetail db;
        private ZcrDetail zcr;
        private MetricDetail pauseRatio;
    }

    @Getter @Builder
    public static class MetricDetail {
        private double targetValue;
        private String unit;
        private String guideMessage;
    }

    @Getter @Builder
    public static class ZcrDetail {
        private double masterMean;
        private double masterStd;
        private String guideMessage;
    }
}