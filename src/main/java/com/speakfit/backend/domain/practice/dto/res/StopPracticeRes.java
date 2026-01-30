package com.speakfit.backend.domain.practice.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StopPracticeRes {
    private Long practiceId;
    private String status;
    private Double time;
}
