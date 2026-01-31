package com.speakfit.backend.domain.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FeedbackStatus {
    GENERATING("생성 중"),
    COMPLETED("생성 완료"),
    FAILED("생성 실패");

    private final String description;
}
