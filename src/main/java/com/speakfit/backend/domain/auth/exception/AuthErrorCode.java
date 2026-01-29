package com.speakfit.backend.domain.auth.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseCode {

    REQUIRED_TERM_NOT_AGREED(HttpStatus.BAD_REQUEST, "AUTH400_1", "필수 약관에 동의해야 합니다."),

    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_1", "아이디 또는 비밀번호가 일치하지 않습니다."),

    STYLE_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH404_1", "존재하지 않는 발표 스타일입니다."),


    DUPLICATE_USERS_ID(HttpStatus.CONFLICT, "AUTH409_1", "이미 사용 중인 아이디입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "AUTH409_2", "이미 사용 중인 닉네임입니다."),
    DUPLICATE_PHONE(HttpStatus.CONFLICT, "AUTH409_3", "이미 사용 중인 전화번호입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
