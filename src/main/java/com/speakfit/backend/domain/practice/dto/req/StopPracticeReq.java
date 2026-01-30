package com.speakfit.backend.domain.practice.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@NoArgsConstructor
public class StopPracticeReq {

    @NotNull(message = "연습 시간은 필수입니다.")
    private Double time;

    // 음성 파일
    private MultipartFile audio;
}
