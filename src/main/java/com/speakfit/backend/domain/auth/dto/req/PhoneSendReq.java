package com.speakfit.backend.domain.auth.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PhoneSendReq {

    @NotBlank
    @Pattern(regexp = "^01[016789]\\d{7,8}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
    private String phoneNum;
}
