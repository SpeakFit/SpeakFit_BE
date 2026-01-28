package com.speakfit.backend.domain.script.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
public class AddScriptReq {
    @Getter
    @NoArgsConstructor
    public static class Request{

        @NotBlank(message = "대본 제목은 필수입니다.")
        @Size(max = 100,message = "제목은 100자를 넘을 수 없습니다.")
        private String title;

        @NotBlank(message = "대본 내용은 필수입니다.")
        @Size(max = 20000,message = "대본 내용은 20000자를 넘을 수 없습니다.")
        private String content;
    }
}
