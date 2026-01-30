package com.speakfit.backend.global.apiPayload.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseCode {

    OK(HttpStatus.OK, "COMMON200", "성공적으로 요청을 처리했습니다."),
    PHONE_CODE_SENT(HttpStatus.OK, "COMMON200_2", "인증번호를 발송했습니다."),
    PHONE_VERIFIED(HttpStatus.OK, "COMMON200_3", "전화번호 인증이 완료되었습니다."),
    CREATED(HttpStatus.CREATED,"COMMON201", "성공적으로 생성되었습니다."),
    NO_CONTENT(HttpStatus.NO_CONTENT, "COMMON204", "성공적으로 처리되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}