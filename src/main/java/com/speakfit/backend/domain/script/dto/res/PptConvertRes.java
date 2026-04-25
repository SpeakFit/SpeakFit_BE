package com.speakfit.backend.domain.script.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class PptConvertRes {
    @Getter
    @NoArgsConstructor
    public static class Response {
        @JsonProperty("sourcePptUrl")
        private String sourcePptUrl;

        @JsonProperty("totalSlides")
        private Integer totalSlides;

        @JsonProperty("slides")
        private List<SlideRes> slides;
    }

    @Getter
    @NoArgsConstructor
    public static class SlideRes {
        @JsonProperty("page")
        private Integer page;

        @JsonProperty("imageUrl")
        private String imageUrl;
    }
}
