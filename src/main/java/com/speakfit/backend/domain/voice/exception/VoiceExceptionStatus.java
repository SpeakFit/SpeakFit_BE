package com.speakfit.backend.domain.voice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VoiceExceptionStatus {

    VOICE_DATA_INSUFFICIENT(false, 400, "분석을 위한 음성 데이터가 부족합니다. 세 문장을 모두 읽어주세요."),
    VOICE_UNPROCESSABLE(false, 422, "목소리가 감지되지 않았습니다. 조용한 곳에서 다시 녹음해주세요."),
    VOICE_ANALYSIS_CONFLICT(false, 409, "이미 음색 분석이 진행 중입니다. 잠시만 기다려주세요."),
    VOICE_ANALYSIS_NOT_FOUND(false, 404, "요청한 분석 결과를 찾을 수 없습니다."),
    VOICE_ANALYSIS_FAILED(false, 502, "음성 분석에 실패했습니다. 잠시 후 다시 시도해주세요."),
    VOICE_ANALYSIS_TIMEOUT(false, 504, "음성 분석 요청 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."),
    VOICE_USER_NOT_FOUND(false, 404, "존재하지 않는 사용자입니다.");

    private final boolean isSuccess;
    private final int code;
    private final String message;
}
