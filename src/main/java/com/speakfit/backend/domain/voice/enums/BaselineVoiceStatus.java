package com.speakfit.backend.domain.voice.enums;

public enum BaselineVoiceStatus {
    // 분석 요청이 생성되어 Python 분석 결과를 기다리는 상태
    PENDING,

    // 기준 음성 분석이 완료되어 사용자 기본 음성으로 반영된 상태
    COMPLETED,

    // 기준 음성 분석 중 오류가 발생했거나 분석값을 얻지 못한 상태
    FAILED
}
