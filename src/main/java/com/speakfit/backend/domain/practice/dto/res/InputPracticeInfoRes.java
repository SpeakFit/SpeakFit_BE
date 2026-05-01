package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.style.enums.StyleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class InputPracticeInfoRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private List<StyleItem> styleList;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleItem {
        private Long styleId;
        private StyleType styleType;
        private String description;
        private String guideAudioUrl;
        private Boolean isRecommended;
    }
}
