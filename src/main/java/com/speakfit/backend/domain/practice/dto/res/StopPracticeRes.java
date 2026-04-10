package com.speakfit.backend.domain.practice.dto.res;

import com.speakfit.backend.domain.practice.enums.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopPracticeRes {

    private Long practiceId;

    private Status status;

    private String audioUrl;
}
