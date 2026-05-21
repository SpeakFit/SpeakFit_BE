package com.speakfit.backend.domain.metric.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "master_delivery_metric")
public class MasterDeliveryMetric {
    // db/ZCR/Pause 의 앵커 기준값

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "master_intensity", nullable = false)
    private Double masterIntensity;

    @Column(name = "master_zcr", nullable = false)
    private Double masterZcr;

    @Column(name = "master_zcr_std", nullable = false)
    private Double masterZcrStd;

    @Column(name = "master_pause_ratio", nullable = false)
    private Double masterPauseRatio;

    @Column(name = "total_analyzed_sentences", nullable = false)
    private Long totalAnalyzedSentences;

    @Column(name = "description", nullable = true, length = 255)
    private String description;
}
