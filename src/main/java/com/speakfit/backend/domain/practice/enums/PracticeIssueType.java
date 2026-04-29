package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum PracticeIssueType {
    TOO_FAST("말이 너무 빠름"),
    TOO_SLOW("말이 너무 느림"),
    LONG_PAUSE("긴 쉼"),
    LOW_CONFIDENCE("낮은 발화 신뢰도"),
    SKIPPED_WORDS("누락된 단어"),
    VOLUME_DROP("성량 저하"),
    PITCH_UNSTABLE("불안정한 높낮이"),
    LOW_SCORE("낮은 문장 점수");

    private final String description;

    // Python 응답 이슈 유형 문자열을 안전하게 PracticeIssueType으로 변환
    public static PracticeIssueType fromString(String value, PracticeIssueType defaultType) {
        if (value == null || value.isBlank()) {
            return defaultType;
        }

        String normalizedValue = value.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replace("-", "_")
                .replace(" ", "_")
                .toUpperCase(Locale.ROOT);

        for (PracticeIssueType type : values()) {
            if (type.name().equals(normalizedValue)) {
                return type;
            }
        }

        return defaultType;
    }
}
