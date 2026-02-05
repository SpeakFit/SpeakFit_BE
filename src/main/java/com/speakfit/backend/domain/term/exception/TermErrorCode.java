package com.speakfit.backend.domain.term.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermErrorCode implements BaseCode {

    TERM_NOT_FOUND( HttpStatus.NOT_FOUND, "TERM404", "존재하지 않는 약관입니다."),

    TERMS_EMPTY(HttpStatus.NOT_FOUND, "TERM404_1", "등록된 약관이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
