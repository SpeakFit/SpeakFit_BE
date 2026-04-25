package com.speakfit.backend.domain.script.dto.res;

import com.speakfit.backend.domain.script.enums.ScriptType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class GetScriptDetailRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String content; // String (Text)
        private String markedContent; // String (Text)
        private ScriptType scriptType;
        private LocalDateTime createdAt;

        private PptInfoRes pptInfo; // PPT 정보 (없으면 null)
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PptInfoRes {
        private String pptUrl; // String (Text)
        private List<PptSlideRes> slides;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PptSlideRes {
        private Integer page;
        private String imageUrl; // String (Text)
    }
}
