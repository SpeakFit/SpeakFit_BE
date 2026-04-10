package com.speakfit.backend.domain.practice.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

public class StopPracticeReq {

    @Getter
    @Setter
    @Schema(name = "StopPracticeRequest")
    public static class Request {
        @NotNull(message = "녹음 파일은 필수입니다.")
        private MultipartFile audio;

        @NotNull(message = "연습 시간은 필수입니다.")
        private Double time; // 연습 총 시간 (초 단위)
    }
}
