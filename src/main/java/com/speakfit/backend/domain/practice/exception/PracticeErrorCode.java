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
    // 400 Bad Request
    PRACTICE_AUDIO_EMPTY(HttpStatus.BAD_REQUEST, "PRACTICE400_1", "녹음 파일을 업로드해주세요."),
    // 404 Not Found
    PRACTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "PRACTICE404_1", "해당 ID의 발표 연습 기록을 찾을 수 없습니다."),
    PRACTICE_AUDIOURL_NOT_FOUND(HttpStatus.NOT_FOUND, "PRACTICE404_2", "해당 음성파일이 없습니다."),
    // 500 Internal Server Error
    PRACTICE_AUDIO_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PRACTICE500_1", "녹음 파일 저장에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
