package com.speakfit.backend.global.apiPayload.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseCode {

    OK(HttpStatus.OK, "COMMON200", "Request processed successfully."),
    CREATED(HttpStatus.CREATED, "COMMON201", "Resource created successfully."),
    ACCEPTED(HttpStatus.ACCEPTED, "COMMON202", "Request has been accepted for processing."),
    NO_CONTENT(HttpStatus.NO_CONTENT, "COMMON204", "Request processed successfully.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
