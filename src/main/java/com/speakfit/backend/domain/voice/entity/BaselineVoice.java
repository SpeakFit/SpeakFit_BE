package com.speakfit.backend.domain.voice.entity;

import com.speakfit.backend.domain.user.entity.User;
import com.speakfit.backend.domain.voice.enums.BaselineVoiceStatus;
import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "baseline_voice")
public class BaselineVoice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "avg_pitch")
    private Double avgPitch;

    @Column(name = "avg_wpm")
    private Double avgWpm;

    @Column(name = "avg_intensity")
    private Double avgIntensity;

    @Column(name = "avg_zcr")
    private Double avgZcr;

    @Column(name = "pause_ratio")
    private Double pauseRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BaselineVoiceStatus status = BaselineVoiceStatus.PENDING;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    @Column(name = "analysis_raw_json", columnDefinition = "TEXT")
    private String analysisRawJson;

    public void updateAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public void complete(Double avgPitch, Double avgWpm, Double avgIntensity, Double avgZcr,
                         Double pauseRatio, String analysisRawJson) {
        this.avgPitch = avgPitch;
        this.avgWpm = avgWpm;
        this.avgIntensity = avgIntensity;
        this.avgZcr = avgZcr;
        this.pauseRatio = pauseRatio;
        this.analysisRawJson = analysisRawJson;
        this.status = BaselineVoiceStatus.COMPLETED;
        this.isActive = true;
    }

    public void fail(String analysisRawJson) {
        this.analysisRawJson = analysisRawJson;
        this.status = BaselineVoiceStatus.FAILED;
        this.isActive = false;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
