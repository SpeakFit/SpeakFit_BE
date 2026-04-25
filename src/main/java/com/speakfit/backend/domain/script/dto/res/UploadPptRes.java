package com.speakfit.backend.domain.script.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class UploadPptRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long scriptId;
        private PptInfoRes pptInfo;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PptInfoRes {
        private String sourcePptUrl;
        private Integer totalSlides;
        private List<PptSlideRes> slides;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PptSlideRes {
        private Integer page;
        private String imageUrl;
    }
}
