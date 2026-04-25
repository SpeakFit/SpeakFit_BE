package com.speakfit.backend.domain.script.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class AddScriptRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String content;
        private List<ContentRes> contentList;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentRes {
        private Integer index;
        private String word;

        @JsonProperty("hasBreak")
        private boolean hasBreak;

        @JsonProperty("isEmphasis")
        private boolean emphasis;
    }
}
