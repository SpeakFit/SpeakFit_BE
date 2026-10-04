package com.speakfit.backend.domain.auth.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseCode {

    /* ===================== SIGN UP ===================== */
    REQUIRED_TERM_NOT_AGREED(HttpStatus.BAD_REQUEST, "AUTH400_1", "필수 약관에 동의해야 합니다."),

    /* ===================== LOGIN ===================== */
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_1", "이메일 또는 비밀번호가 일치하지 않습니다."),

    /* ===================== TOKEN REFRESH ===================== */
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_2", "유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요."),

    /* ===================== DUPLICATE ===================== */
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH409_1", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "AUTH409_2", "이미 사용 중인 닉네임입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
