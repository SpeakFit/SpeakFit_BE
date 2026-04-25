package com.speakfit.backend.domain.script.dto.res;

import com.speakfit.backend.domain.script.enums.ScriptType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class GetScriptListRes {
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String title;
        private String content;
        private ScriptType scriptType;
        private LocalDateTime createdAt;
    }
}
