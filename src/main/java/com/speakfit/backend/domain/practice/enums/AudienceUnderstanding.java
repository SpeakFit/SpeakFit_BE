package com.speakfit.backend.domain.practice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AudienceUnderstanding {
    LOW("낮음"),
    MIDDLE("보통"),
    HIGH("높음");

    private final String description;
}
