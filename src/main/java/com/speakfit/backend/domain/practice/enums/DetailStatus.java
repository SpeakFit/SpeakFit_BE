package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum DetailStatus {
    NORMAL("정상"),
    FAST("빠름"),
    SLOW("느림"),
    MISMATCH("불일치");

    private final String description;

    // Python 응답 상태 문자열을 안전하게 DetailStatus로 변환
    public static DetailStatus fromString(String value, DetailStatus defaultStatus) {
        if (value == null || value.isBlank()) {
            return defaultStatus;
        }

        String normalizedValue = value.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace("-", "_")
                .replace(" ", "_")
                .toUpperCase(Locale.ROOT);

        for (DetailStatus status : values()) {
            if (status.name().equals(normalizedValue)) {
                return status;
            }
        }

        return defaultStatus;
    }
}
