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
    private Double avgIntensity;

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

    public void updateStyleData(String mostSimilarStyle, Integer matchingRate, String voiceStyleDescription) {
        this.mostSimilarStyle = mostSimilarStyle;
        this.matchingRate = matchingRate;
        this.voiceStyleDescription = voiceStyleDescription;
    }
}