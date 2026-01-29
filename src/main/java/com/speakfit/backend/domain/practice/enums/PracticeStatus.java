package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PracticeStatus {
    RECORDING("녹음중"),
   COMPLETED("연습 완료"),
   ANALYZING("분석중"),
   ANALYZED("분석 완료");

    private final String description;
}
