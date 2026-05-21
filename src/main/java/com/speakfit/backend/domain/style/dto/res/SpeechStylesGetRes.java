package com.speakfit.backend.domain.style.dto.res;

import com.speakfit.backend.domain.style.enums.StyleType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SpeechStylesGetRes {

    private List<StyleItem> styles;

    @Getter
    @Builder
    public static class StyleItem{
        private Long styleId;
        private StyleType styleType;
        private String displayName;
        private String description;
        private String sampleAudioUrl;
    }
}
