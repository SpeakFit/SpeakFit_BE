package com.speakfit.backend.domain.metric.entity;

import com.speakfit.backend.domain.practice.enums.AudienceType;
import com.speakfit.backend.domain.practice.enums.AudienceUnderstanding;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(
        name = "target_audience_metric",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_target_audience_metric_condition",
                        columnNames = {"audience_understanding", "audience_type"}
                )
        }
)
public class TargetAudienceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_understanding", nullable = false, length = 20)
    private AudienceUnderstanding audienceUnderstanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private AudienceType audienceType;

    @Column(name = "min_wpm", nullable = false)
    private Double minWpm;

    @Column(name = "max_wpm", nullable = false)
    private Double maxWpm;

    @Column(name = "min_pitch_ratio", nullable = false)
    private Double minPitchRatio;

    @Column(name = "max_pitch_ratio", nullable = false)
    private Double maxPitchRatio;

    @Column(name = "min_intensity_delta", nullable = false)
    private Double minIntensityDelta;

    @Column(name = "max_intensity_delta", nullable = false)
    private Double maxIntensityDelta;

    @Column(name = "min_pause_ratio", nullable = false)
    private Double minPauseRatio;

    @Column(name = "max_pause_ratio", nullable = false)
    private Double maxPauseRatio;
}
