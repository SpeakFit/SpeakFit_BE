package com.speakfit.backend.global.apiPayload.exception;

import com.speakfit.backend.domain.voice.exception.VoiceException;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Object>> handleCustom(CustomException e) {
        BaseCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(code, null));
    }

    // VoiceException 핸들러 추가
    @ExceptionHandler(VoiceException.class)
    public ResponseEntity<ApiResponse<Object>> handleVoiceException(VoiceException e) {
        log.warn("Voice analysis error: {}", e.getMessage());

        BaseCode errorCode = new BaseCode() {
            @Override
            public String getCode() {
                return "COMMON400";
            }

            @Override
            public String getMessage() {
                return e.getMessage();
            }

            @Override
            public HttpStatus getHttpStatus() { // 반환 타입을 HttpStatus로 수정
                return HttpStatus.BAD_REQUEST;
            }
        };

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.onFailure(errorCode, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValid(MethodArgumentNotValidException e) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode.VALIDATION_ERROR, null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity
                .status(ErrorCode.PAYLOAD_TOO_LARGE.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode.PAYLOAD_TOO_LARGE, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAll(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode.INTERNAL_SERVER_ERROR, null));
    }
}
