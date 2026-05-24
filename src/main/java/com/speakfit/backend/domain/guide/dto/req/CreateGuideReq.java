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
}