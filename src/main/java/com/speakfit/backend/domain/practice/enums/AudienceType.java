package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AudienceType {
    SENIOR("노년"),
    ADULT("성인"),
    YOUTH("청소년"),
    CHILD("아동");

    private final String description;
}
