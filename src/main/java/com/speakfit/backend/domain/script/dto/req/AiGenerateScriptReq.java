package com.speakfit.backend.domain.script.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AiGenerateScriptReq {
    @Getter
    @NoArgsConstructor
    @Schema(name = "AiGenerateScriptRequest")
    public static class Request {

        @NotBlank(message = "발표 주제는 필수입니다.")
        @Size(max = 255, message = "발표 주제는 255자를 넘을 수 없습니다.")
        private String topic;

        @NotNull(message = "발표 시간은 필수입니다.")
        @Positive(message = "발표 시간은 1분 이상이어야 합니다.")
        private Integer time;

        @NotNull(message = "청중 연령대는 필수입니다.")
        private AudienceAge audienceAge;

        @NotNull(message = "청중 지식수준은 필수입니다.")
        private AudienceLevel audienceLevel;

        @NotNull(message = "스피치 유형은 필수입니다.")
        private SpeechType speechType;

        @NotBlank(message = "발표 목적은 필수입니다.")
        @Size(max = 1000, message = "발표 목적은 1000자를 넘을 수 없습니다.")
        private String purpose;

        @Size(max = 255, message = "강조 키워드는 255자를 넘을 수 없습니다.")
        private String keywords;
    }

    public enum AudienceAge {
        CHILD,
        YOUTH,
        ADULT,
        ELDERLY
    }

    public enum AudienceLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum SpeechType {
        PRESENTATION,
        INTERVIEW,
        LECTURE,
        DISCUSSION,
        FEEDBACK
    }
}
