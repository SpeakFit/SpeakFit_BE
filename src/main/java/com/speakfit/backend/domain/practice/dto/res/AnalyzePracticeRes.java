package com.speakfit.backend.domain.practice.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AnalyzePracticeRes {
    private Long practiceId;
    private String status;
    private String message;
}
