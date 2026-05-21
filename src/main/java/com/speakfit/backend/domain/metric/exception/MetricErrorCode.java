package com.speakfit.backend.domain.metric.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MetricErrorCode implements BaseCode {

    SPEECH_STYLE_MATRIX_NOT_FOUND(HttpStatus.NOT_FOUND, "METRIC404_1", "해당 조건의 스피치 스타일 매트릭스를 찾을 수 없습니다."),
    TARGET_AUDIENCE_METRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "METRIC404_2", "해당 조건의 청중별 지표를 찾을 수 없습니다."),
    MASTER_DELIVERY_METRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "METRIC404_3", "전달력 마스터 지표를 찾을 수 없습니다."),
    METRIC_THRESHOLD_NOT_FOUND(HttpStatus.NOT_FOUND, "METRIC404_4", "해당 지표의 임계값을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
