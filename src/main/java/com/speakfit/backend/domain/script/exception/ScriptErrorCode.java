package com.speakfit.backend.domain.script.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ScriptErrorCode implements BaseCode {
    SCRIPT_EMPTY_INPUT(HttpStatus.BAD_REQUEST, "SCRIPT400_1", "발표 대본 제목 또는 내용을 입력해주세요."),
    SCRIPT_AI_GENERATE_FAILED(HttpStatus.BAD_GATEWAY, "SCRIPT502_1", "AI 발표 대본 생성에 실패했습니다."),
    SCRIPT_AI_UPDATE_FAILED(HttpStatus.BAD_GATEWAY, "SCRIPT502_2", "AI 발표 대본 최적화에 실패했습니다."),
    SCRIPT_PPT_EMPTY_FILE(HttpStatus.BAD_REQUEST, "SCRIPT400_2", "PPT 파일을 업로드해주세요."),
    SCRIPT_PPT_INVALID_EXTENSION(HttpStatus.BAD_REQUEST, "SCRIPT400_3", "PPT 또는 PPTX 파일만 업로드할 수 있습니다."),
    SCRIPT_PPT_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SCRIPT500_1", "PPT 파일 저장에 실패했습니다."),
    SCRIPT_PPT_CONVERT_FAILED(HttpStatus.BAD_GATEWAY, "SCRIPT502_3", "PPT 슬라이드 변환에 실패했습니다."),
    SCRIPT_PPT_ALREADY_PROCESSING(HttpStatus.CONFLICT, "SCRIPT409_1", "PPT 변환이 이미 진행 중입니다."),
    SCRIPT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "SCRIPT403_1", "해당 발표대본에 대한 접근 권한이 없습니다."),
    SCRIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "SCRIPT404_1", "해당 ID의 발표대본을 찾을 수 없습니다."),
    SCRIPT_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "SCRIPT404_2", "존재하지 않는 사용자입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
