package com.speakfit.backend.domain.practice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "analysis_result")
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "record_id", nullable = false)
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
}