package com.speakfit.backend.domain.practice.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.media.Schema;


public class StopPracticeReq {

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "발표 연습 종료 요청 객체")
    public static class StopPracticeRequest {

        @Schema(description = "연습 시간 (초 단위)", example = "120.5")
        @PositiveOrZero
        @NotNull(message = "연습 시간은 필수입니다.")
        private Double time;

        // 음성 파일
        @Schema(description = "녹음된 오디오 파일", type = "string", format = "binary")
        private MultipartFile audio;
    }
}
