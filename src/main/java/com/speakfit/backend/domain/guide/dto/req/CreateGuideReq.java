package com.speakfit.backend.domain.guide.dto.req;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// 최종 가이드 생성 요청
public class CreateGuideReq {
    private Long baselineVoiceId;
    private String selectedStyle; // 열정적인 스타일, 지적인 스타일, 신중한 스타일, 전달력 있는 스타일
    // [STEP 3-C] §8 청중 오프셋 적용을 위한 audience 필드 (optional — null이면 §6 Gold Standard 그대로 사용)
    private String audienceUnderstanding; // LOW | MID | HIGH
    private String audienceAgeGroup;      // CHILD | TEEN | ADULT | SENIOR
}