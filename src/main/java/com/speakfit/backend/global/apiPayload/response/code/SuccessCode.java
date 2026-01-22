package com.speakfit.backend.global.apiPayload.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseCode {

    OK("COMMON200", "성공적으로 요청을 처리했습니다."),
    CREATED("COMMON201", "성공적으로 생성되었습니다."),
    NO_CONTENT("COMMON204", "성공적으로 처리되었습니다.");

    private final String code;
    private final String message;
}