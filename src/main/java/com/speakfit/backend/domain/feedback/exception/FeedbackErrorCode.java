package com.speakfit.backend.domain.feedback.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FeedbackErrorCode implements BaseCode {
    // 피드백 관련 에러 (400 Bad Request)
    FEEDBACK_INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "FEEDBACK400_1", "유효한 날짜가 아닙니다."),
    FEEDBACK_NO_DATA_IN_PERIOD(HttpStatus.BAD_REQUEST, "FEEDBACK400_2", "해당 기간 내에 분석 완료된 연습 기록이 없습니다."),
    // 피드백 접근 권한 에러 (403 Forbidden)
    FEEDBACK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FEEDBACK403_1", "해당 피드백에 대한 접근 권한이 없습니다."),

    // 피드백 조회 관련 (404 Not Found)
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "FEEDBACK404_1", "해당 피드백 기록을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
