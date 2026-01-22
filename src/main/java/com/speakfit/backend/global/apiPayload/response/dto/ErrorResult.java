package com.speakfit.backend.global.apiPayload.response.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class ErrorResult {
    private final String reason;
    private final Map<String, String> fieldErrors;
}
