package com.speakfit.backend.global.apiPayload.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode implements BaseCode {

    /* ===================== Common ===================== */
    BAD_REQUEST(
            "COMMON400",
            "잘못된 요청입니다.",
            HttpStatus.BAD_REQUEST
    ),

    UNAUTHORIZED(
            "COMMON401",
            "인증이 필요합니다.",
            HttpStatus.UNAUTHORIZED
    ),

    FORBIDDEN(
            "COMMON403",
            "접근 권한이 없습니다.",
            HttpStatus.FORBIDDEN
    ),

    NOT_FOUND(
            "COMMON404",
            "요청한 리소스를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    ),

    INTERNAL_SERVER_ERROR(
            "COMMON500",
            "서버 오류가 발생했습니다.",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),

    /* ===================== Validation ===================== */
    INVALID_REQUEST(
            "VALID400",
            "요청 형식이 올바르지 않습니다.",
            HttpStatus.BAD_REQUEST
    ),

    VALIDATION_ERROR(
            "VALID401",
            "요청 값이 유효하지 않습니다.",
            HttpStatus.BAD_REQUEST
    ),

    /* ===================== DB ===================== */
    DATABASE_ERROR(
            "DB500",
            "데이터 처리 중 오류가 발생했습니다.",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
