package com.speakfit.backend.domain.guide.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GuideErrorCode implements BaseCode {

    // 404 Not Found 예외 스펙
    BASE_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "GUIDE4041", "가이드라인 생성을 위한 기준 연습 기록(Baseline)을 찾을 수 없습니다."),
    GUIDE_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "GUIDE4042", "존재하지 않는 사용자 정보입니다."),

    // 400 Bad Request 예외 스펙
    CLUSTER_MATRIX_NOT_FOUND(HttpStatus.BAD_REQUEST, "GUIDE4001", "해당 지역 및 성별 그룹에 매핑된 스피치 스타일 군집 데이터가 존재하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}