package com.speakfit.backend.domain.practice.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
@AllArgsConstructor
public enum PracticeErrorCode implements BaseCode {

    // 403 Forbidden
    PRACTICE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PRACTICE403_1", "해당 발표 연습 기록에 대한 접근 권한이 없습니다."),
    // 404 Not Found
    PRACTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "PRCEATICE404_1", "해당 ID의 발표 연습 기록을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}