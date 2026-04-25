package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.style.enums.StyleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class SelectStyleRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private StyleType styleType;
        private List<ContentRes> contentList;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentRes {
        private Integer index;
        private String word;
        private boolean hasBreak;
        private boolean isEmphasis;
    }
}
