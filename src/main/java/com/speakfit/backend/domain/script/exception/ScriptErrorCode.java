package com.speakfit.backend.domain.script.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
@AllArgsConstructor
public enum ScriptErrorCode implements BaseCode {
    // 400 Bad Request
    SCRIPT_EMPTY_INPUT(HttpStatus.BAD_REQUEST, "SCRIPT400_1", "발표 대본 제목 또는 내용을 입력해주세요."),
    // 404 Not Found
    SCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "SCRIPT404_1", "해당 ID의 대본을 찾을 수 없습니다."),
    SCRIPT_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "SCRIPT404_2", "존재하지 않는 사용자입니다."); // <-- 여기 추가!
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
   }
