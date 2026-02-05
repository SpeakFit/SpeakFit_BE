package com.speakfit.backend.domain.style.dto.res;

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
        private String name;
        private String sampleAudioUrl;
    }
}
