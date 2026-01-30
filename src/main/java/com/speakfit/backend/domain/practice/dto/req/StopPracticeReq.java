package com.speakfit.backend.domain.practice.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;


public class StopPracticeReq {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Request {


        @PositiveOrZero
        @NotNull(message = "연습 시간은 필수입니다.")
        private Double time;

        // 음성 파일
        private MultipartFile audio;
    }
}
