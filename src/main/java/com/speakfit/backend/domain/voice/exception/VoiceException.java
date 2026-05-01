package com.speakfit.backend.domain.voice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class VoiceException extends RuntimeException {

    private final VoiceExceptionStatus status;

    @Override
    public String getMessage() {
        return status.getMessage();
    }
}