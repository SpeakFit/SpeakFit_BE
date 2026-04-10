package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SpeechInformation {
    PRESENTATION("발표"),
    INTERVIEW("면접"),
    LECTURE("강의"),
    PERSUASION("설득");

    private final String description;
}
