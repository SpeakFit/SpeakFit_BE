package com.speakfit.backend.global.apiPayload.exception;

import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final BaseCode errorCode;

    public CustomException(BaseCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(BaseCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
