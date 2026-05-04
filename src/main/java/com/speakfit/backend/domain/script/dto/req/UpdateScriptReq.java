package com.speakfit.backend.domain.script.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class UpdateScriptReq {

    @Getter
    @NoArgsConstructor
    public static class Request {

        @NotBlank(message = "발표 대본 제목은 필수입니다.")
        @Size(max = 255, message = "발표 대본 제목은 255자를 넘을 수 없습니다.")
        private String title;

        @NotBlank(message = "발표 대본 내용은 필수입니다.")
        private String content;
    }
}
