package com.speakfit.backend.global.apiPayload.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.speakfit.backend.global.apiPayload.response.ApiResponse;
import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import com.speakfit.backend.global.apiPayload.response.code.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestControllerAdvice(annotations = {RestController.class})
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

    /**
     * ✅ @RequestParam, @PathVariable 등 Bean Validation 실패
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException e, WebRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());

        ApiResponse<Object> body = ApiResponse.onFailure(ErrorCode.INVALID_REQUEST, message);

        return handleExceptionInternal(
                e, body, new HttpHeaders(),
                ErrorCode.INVALID_REQUEST.getHttpStatus(),
                request
        );
    }

    /**
     * ✅ @Valid @RequestBody DTO 검증 실패
     * - 필드별 에러를 Map으로 내려줌
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String field = fieldError.getField();
            String msg = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
            errors.merge(field, msg, (a, b) -> a + ", " + b);
        });

        ApiResponse<Object> body = ApiResponse.onFailure(ErrorCode.VALIDATION_ERROR, errors);

        return handleExceptionInternal(
                e, body, headers,
                ErrorCode.VALIDATION_ERROR.getHttpStatus(),
                request
        );
    }

    /**
     * ✅ Enum/타입 변환 실패 (예: Gender="MAIL" 같은 오타)
     */
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidFormat(InvalidFormatException ex) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode.INVALID_REQUEST, null));
    }

    /**
     * ✅ 도메인 CustomException
     * - 서비스/도메인에서 ErrorCode를 들고 던지는 방식
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleCustomException(CustomException e) {
        BaseCode code = e.getErrorCode();
        return ResponseEntity
                .status(code.getHttpStatus())
                .body(ApiResponse.onFailure(code, null));
    }

    /**
     * ✅ DB 유니크 제약조건 충돌 (레이스컨디션 대비)
     * - 회원가입 중복 같은 케이스를 여기서도 안전하게 커버 가능
     * - (AuthService에서 이미 잡아도, 혹시 누락되면 여기서 잡힘)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        // 글로벌 레벨에서는 구체 원인 분기 안 하고 DB 에러로 통일
        // (원하면 uk_* 보고 INVALID_REQUEST로 세분화도 가능)
        return ResponseEntity
                .status(ErrorCode.DATABASE_ERROR.getHttpStatus())
                .body(ApiResponse.onFailure(ErrorCode.DATABASE_ERROR, null));
    }

    /**
     * ✅ 최후의 보루: 미처리 예외 → 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnknownException(Exception e, WebRequest request) {
        log.error("Unhandled exception", e);

        ApiResponse<Object> body = ApiResponse.onFailure(ErrorCode.INTERNAL_SERVER_ERROR, null);

        return handleExceptionInternal(
                e, body, new HttpHeaders(),
                ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus(),
                request
        );
    }
}
