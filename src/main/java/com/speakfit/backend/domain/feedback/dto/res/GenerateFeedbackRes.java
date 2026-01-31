package com.speakfit.backend.domain.feedback.dto.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateFeedbackRes {
    private Long feedbackId;
    private String status;
    private String message;
}