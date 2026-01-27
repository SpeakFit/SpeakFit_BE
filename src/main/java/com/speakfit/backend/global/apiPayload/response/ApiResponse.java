package com.speakfit.backend.global.apiPayload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean isSuccess;
    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T result;

    /* ===================== SUCCESS ===================== */

    public static <T> ApiResponse<T> onSuccess(BaseCode code, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .code(code.getCode())
                .message(code.getMessage())
                .result(result)
                .build();
    }

    /* ===================== FAILURE ===================== */

    public static <T> ApiResponse<T> onFailure(BaseCode code, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(false)
                .code(code.getCode())
                .message(code.getMessage())
                .result(result)
                .build();
    }
}
