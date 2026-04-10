package com.speakfit.backend.domain.script.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AddScriptReq {
    @Getter
    @NoArgsConstructor
    @Schema(name = "AddScriptRequest")
    public static class Request {

        @NotBlank(message = "대본 제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자를 넘을 수 없습니다.") // SQL 스키마에 맞춰 확장
        private String title;

        @NotBlank(message = "대본 내용은 필수입니다.")
        @Size(max = 20000, message = "대본 내용은 20000자를 넘을 수 없습니다.")
        private String content;
    }
}
