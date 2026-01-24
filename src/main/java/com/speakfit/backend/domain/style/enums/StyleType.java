package com.speakfit.backend.domain.style.enums;

public enum StyleType {
    CALM_LOW_TONE("중저음의 신중하고 차분한 스타일"),
    STANDARD_LECTURE("안정적인 톤의 표준 강의 스타일"),
    ENERGETIC_FAST("에너지 넘치는 고음/빠른 스타일");

    private final String displayName;

    StyleType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
