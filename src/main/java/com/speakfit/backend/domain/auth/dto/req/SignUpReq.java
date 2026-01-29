package com.speakfit.backend.domain.auth.dto.req;


import com.speakfit.backend.domain.term.enums.TermType;
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
        @Pattern(regexp = "^[a-zA-Z가-힣]{2,10}$")
        private String name;

        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String birth;

        @NotBlank
        @Pattern(regexp = "^\\d{11}$")
        private String phoneNum;

        @NotBlank
        @Pattern(regexp = "^[a-zA-Z0-9@._-]{6,18}$")
        private String usersId;

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
        private Long styleId;

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
        private TermType termType;

        @NotNull
        private Boolean agreed;
    }
}