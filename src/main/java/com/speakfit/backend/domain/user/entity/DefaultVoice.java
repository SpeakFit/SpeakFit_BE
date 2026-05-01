package com.speakfit.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자 기본 음색 및 발화 속도 정보를 나타내는 값 타입 클래스입니다.
// @Embeddable 어노테이션을 통해 User 엔티티 내에 포함되어 함께 관리됩니다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefaultVoice {

    // 기본 평균 피치(Pitch) 지표
    @Column(name = "default_pitch")
    private Double defaultPitch;

    // 기본 평균 발화 속도(WPM) 지표
    @Column(name = "default_wpm")
    private Double defaultWpm;

    // DefaultVoice 생성자 빌더 패턴 적용
    @Builder
    public DefaultVoice(Double defaultPitch, Double defaultWpm) {
        this.defaultPitch = defaultPitch;
        this.defaultWpm = defaultWpm;
    }

    // 기본 음색 지표를 업데이트하는 메서드
    public void updateMetrics(Double defaultPitch, Double defaultWpm) {
        this.defaultPitch = defaultPitch;
        this.defaultWpm = defaultWpm;
    }
}