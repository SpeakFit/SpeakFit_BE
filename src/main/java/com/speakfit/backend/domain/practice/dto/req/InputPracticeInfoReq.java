package com.speakfit.backend.domain.practice.dto.req;

import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import com.speakfit.backend.domain.practice.enums.SpeechInformation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class InputPracticeInfoReq {
    @Getter
    @NoArgsConstructor
    @Schema(name = "InputPracticeInfoRequest")
    public static class Request {
        @NotNull(message = "청중 타입은 필수입니다.")
        private AudienceType audienceType;

        @NotNull(message = "청중 이해도는 필수입니다.")
        private AudienceUnderstanding audienceUnderstanding;

        @NotNull(message = "스피치 정보는 필수입니다.")
        private SpeechInformation speechInformation;

        @NotNull(message = "목표 시간은 필수입니다.")
        private Integer targetTime;
    }
}
