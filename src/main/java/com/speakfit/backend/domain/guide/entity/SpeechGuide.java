package com.speakfit.backend.domain.guide.entity;

import com.speakfit.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "speech_guide")
public class SpeechGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 1. 발화 지표 연산의 기준이 된 baseline_voice 테이블 외래키 매핑
    @Column(name = "baseline_voice_id", nullable = false)
    private Long baselineVoiceId;

    @Column(name = "selected_style", nullable = false)
    private String selectedStyle;

    private Double targetWpm;
    private Double targetPitch;
    private Double targetDb;
    private Double masterZcrMean;
    private Double masterZcrStd;
    private Double targetPauseRatio;

    @Column(columnDefinition = "TEXT")
    private String wpmGuideMessage;

    @Column(columnDefinition = "TEXT")
    private String pitchGuideMessage;
}