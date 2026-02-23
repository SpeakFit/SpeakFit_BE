package com.speakfit.backend.domain.style.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SpeechStyleErrorCode implements BaseCode {

    STYLE_NOT_FOUND(HttpStatus.NOT_FOUND, "STYLE404", "존재하지 않는 발표 스타일입니다."),

    STYLES_EMPTY(HttpStatus.NOT_FOUND, "STYLE404_1", "등록된 발표 스타일이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
