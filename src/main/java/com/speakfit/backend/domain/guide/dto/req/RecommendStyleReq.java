package com.speakfit.backend.domain.guide.dto.req;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RecommendStyleReq {
    private String audienceUnderstanding; // 청중 이해도 (HIGH / LOW)
    private String audienceAgeGroup;      // 청중 연령대 (ADULT / SENIOR 등)
}