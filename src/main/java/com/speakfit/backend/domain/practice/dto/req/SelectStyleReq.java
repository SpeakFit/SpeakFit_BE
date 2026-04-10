package com.speakfit.backend.domain.practice.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class SelectStyleReq {
    @Getter
    @NoArgsConstructor
    @Schema(name = "SelectStyleRequest")
    public static class Request {
        @NotNull(message = "선택한 스타일 ID는 필수입니다.")
        private Long styleId;
    }
}
