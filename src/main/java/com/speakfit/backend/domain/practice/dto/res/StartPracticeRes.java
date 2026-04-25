package com.speakfit.backend.domain.practice.dto.res;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.speakfit.backend.domain.practice.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class StartPracticeRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long practiceId;
        private String title;
        private String webSocketUrl;
        private Status status;
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
        private boolean isEmphasis;
    }
}
