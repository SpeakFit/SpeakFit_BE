package com.speakfit.backend.domain.auth.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class LoginReq {

    @Getter
    @NoArgsConstructor
    @Schema(name = "AuthLoginRequest")
    public static class Request{
        @NotBlank
        private String userId;

        @NotBlank
        private String password;
    }
}
