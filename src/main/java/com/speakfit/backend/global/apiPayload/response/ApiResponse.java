package com.speakfit.backend.global.apiPayload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.speakfit.backend.global.apiPayload.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private final boolean isSuccess;

    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T result;

    public static <T> ApiResponse<T> onSuccess(BaseCode code, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .code(code.getCode())
                .message(code.getMessage())
                .result(result)
                .build();
    }

    public static ApiResponse<Void> onSuccess(BaseCode code) {
        return ApiResponse.<Void>builder()
                .isSuccess(true)
                .code(code.getCode())
                .message(code.getMessage())
                .result(null)
                .build();
    }

    public static ApiResponse<Object> onFailure(BaseCode code, Object data) {
        return ApiResponse.builder()
                .isSuccess(false)
                .code(code.getCode())
                .message(code.getMessage())
                .result(data)
                .build();
    }

    public static ApiResponse<Object> onFailure(BaseCode code) {
        return onFailure(code, null);
    }
}
