package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DetailStatus {
    NORMAL("정상"),
    FAST("빠름"),
    SLOW("느림"),
    MISMATCH("불일치");

    private final String description;
}
