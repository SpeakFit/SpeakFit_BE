package com.speakfit.backend.global.apiPayload.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseCode {

    OK(HttpStatus.OK, "COMMON200", "요청이 성공적으로 처리되었습니다."),
    CREATED(HttpStatus.CREATED, "COMMON201", "리소스가 성공적으로 생성되었습니다."),
    ACCEPTED(HttpStatus.ACCEPTED, "COMMON202", "요청이 접수되어 처리 중입니다."),
    NO_CONTENT(HttpStatus.NO_CONTENT, "COMMON204", "처리는 성공했지만 반환할 내용이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
