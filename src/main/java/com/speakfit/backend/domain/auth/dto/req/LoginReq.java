package com.speakfit.backend.domain.auth.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class LoginReq {

    @Getter
    @NoArgsConstructor
    public static class Request{
        @NotBlank
        private String userId;

        @NotBlank
        private String password;
    }
}
