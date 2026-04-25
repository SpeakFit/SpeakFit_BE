package com.speakfit.backend.domain.auth.dto.req;


import com.speakfit.backend.domain.user.enums.Dialect;
import com.speakfit.backend.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class SignUpReq {

    @Getter
    @NoArgsConstructor
    @Schema(name = "AuthSignUpRequest")
    public static class Request {

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String birthday;

        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-]).{8,20}$"
        )
        private String password;

        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9가-힣_]{2,20}$")
        private String nickname;

        @NotNull
        private Gender gender;

        @NotNull
        private Dialect dialect;

        // 약관 동의 리스트
        @Valid
        @NotNull
        @Size(min = 1, message = "terms must not be empty")
        private List<TermAgreement> terms;
    }

    @Getter
    @NoArgsConstructor
    public static class TermAgreement {

        @NotNull
        private Long termId;

        @NotNull
        private Boolean agreed;
    }
}
