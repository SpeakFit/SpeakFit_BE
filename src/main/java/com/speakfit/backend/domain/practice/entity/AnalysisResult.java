package com.speakfit.backend.domain.practice.entity;

import com.speakfit.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "analysis_result")
public class AnalysisResult extends BaseEntity {

    @Id
    private Long recordId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "record_id")
    private PracticeRecord practiceRecord;

    @Column(name = "avg_wpm")
    private Double avgWpm;

    @Column(name = "avg_pitch")
    private Double avgPitch;

    @Column(name = "avg_intensity")
    private Double avgIntensity;               // dBFS — 내부 평가용

    @Column(name = "volume_score")
    private Integer volumeScore;               // [STEP-D] 0~100 음량 점수 (표시용)

    @Column(name = "volume_level")
    private String volumeLevel;                // [STEP-D] "작음" / "적정" / "큼" (표시용)

    @Column(name = "avg_zcr")
    private Double avgZcr;

    @Column(name = "pause_ratio")
    private Double pauseRatio;

    @Column(name = "wpm_diff")
    private Double wpmDiff;

    @Column(name = "pitch_diff")
    private Double pitchDiff;

    @Column(name = "intensity_diff")
    private Double intensityDiff;

    @Column(name = "zcr_diff")
    private Double zcrDiff;

    @Column(name = "pause_count")
    private Integer pauseCount;

    // CodeRabbit 피드백 반영: 스피치 스타일 관련 필드 추가
    @Column(name = "most_similar_style")
    private String mostSimilarStyle;

    @Column(name = "matching_rate")
    private Integer matchingRate;

    @Column(name = "voice_style_description")
    private String voiceStyleDescription;

    public void updateData(Double avgWpm, Double avgPitch, Double avgIntensity, Double avgZcr,
                           Double pauseRatio, Double wpmDiff, Double pitchDiff,
                           Double intensityDiff, Double zcrDiff, Integer pauseCount) {
        this.avgWpm = avgWpm;
        this.avgPitch = avgPitch;
        this.avgIntensity = avgIntensity;
        this.avgZcr = avgZcr;
        this.pauseRatio = pauseRatio;
        this.wpmDiff = wpmDiff;
        this.pitchDiff = pitchDiff;
        this.intensityDiff = intensityDiff;
        this.zcrDiff = zcrDiff;
        this.pauseCount = pauseCount;
    }

    // [STEP-D] 표시용 음량 필드 갱신
    public void updateVolumeDisplay(Integer volumeScore, String volumeLevel) {
        this.volumeScore = volumeScore;
        this.volumeLevel = volumeLevel;
    }

    public void updateStyleData(String mostSimilarStyle, Integer matchingRate, String voiceStyleDescription) {
        this.mostSimilarStyle = mostSimilarStyle;
        this.matchingRate = matchingRate;
        this.voiceStyleDescription = voiceStyleDescription;
    }
}